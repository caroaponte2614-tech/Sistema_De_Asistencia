package com.schoolplus.services.documentos;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.Documento;
import com.schoolplus.model.DocumentoCategoria;
import com.schoolplus.model.DocumentoVersion;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.DocumentoCategoriaRepository;
import com.schoolplus.repository.DocumentoRepository;
import com.schoolplus.repository.DocumentoVersionRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Documentos de expediente (§4.2, D-017). Dos carpetas por colaborador, con reglas
 * distintas:
 *
 * <ul>
 *   <li><strong>Pública</strong>: la sube el propio dueño (hoja de vida, certificados,
 *       soportes). Cualquiera administra la suya.</li>
 *   <li><strong>Restringida</strong>: la sube, edita o borra quien tenga
 *       {@code DOCUMENTO_RESTRINGIDO_SUBIR} —ADMIN y, desde D-017, RH—. El dueño solo
 *       la lee, nunca la toca.</li>
 * </ul>
 *
 * <p>Cada subida nueva es un {@link Documento} con su primera {@link DocumentoVersion};
 * volver a subir sobre el mismo documento agrega versión, nunca sobrescribe (comentario
 * de cabecera de V5). El archivo vive en el almacenamiento local vía {@link DocumentoAlmacenamiento};
 * "borrar" es marcar {@code archivado}, no destruir el histórico.</p>
 */
@Service
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final DocumentoVersionRepository versionRepository;
    private final DocumentoCategoriaRepository categoriaRepository;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;
    private final NotificacionService notificacionService;

    public DocumentoService(DocumentoRepository documentoRepository,
                            DocumentoVersionRepository versionRepository,
                            DocumentoCategoriaRepository categoriaRepository,
                            DocumentoAlmacenamiento almacenamiento,
                            CapacidadService capacidades,
                            NotificacionService notificacionService) {
        this.documentoRepository = documentoRepository;
        this.versionRepository = versionRepository;
        this.categoriaRepository = categoriaRepository;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
        this.notificacionService = notificacionService;
    }

    // ── Carpeta pública: el dueño sube lo suyo ───────────────────────────────

    @Transactional
    public Documento subirPublico(Usuario actor, String categoriaCodigo, String titulo,
                                  String descripcion, MultipartFile archivo) {
        if (!capacidades.puede(Capacidades.DOCUMENTO_PUBLICO_SUBIR)) {
            throw new PermisoDocumentoException("No tienes permiso para subir documentos.");
        }
        DocumentoCategoria categoria = categoriaDe(categoriaCodigo, ClaseDocumento.PUBLICO);
        return crearDocumentoConPrimeraVersion(actor, actor, categoria, titulo, descripcion, archivo);
    }

    @Transactional(readOnly = true)
    public List<Documento> carpetaPublicaDe(String usuarioId) {
        return documentoRepository.buscarDeLaCarpeta(usuarioId, ClaseDocumento.PUBLICO);
    }

    // ── Carpeta restringida: solo RH/ADMIN suben, editan o borran ────────────

    @Transactional
    public Documento subirRestringido(Usuario actor, Usuario dueño, String categoriaCodigo,
                                      String titulo, String descripcion, MultipartFile archivo) {
        exigirCapacidadRestringidos();
        DocumentoCategoria categoria = categoriaDe(categoriaCodigo, ClaseDocumento.RESTRINGIDO);
        Documento documento = crearDocumentoConPrimeraVersion(dueño, actor, categoria, titulo, descripcion, archivo);

        if (!dueño.getId().equals(actor.getId())) {
            // Sin enlace a propósito: el colaborador todavía no tiene una pantalla propia
            // para leer su carpeta restringida (solo RH/ADMIN la ven, desde el módulo de
            // usuarios) aunque la capacidad DOCUMENTO_RESTRINGIDO_VER en PROPIO ya exista.
            // Enlazar a "Mis documentos" sería engañoso: ahí solo está lo público.
            notificacionService.crear(dueño.getId(), "DOCUMENTO_RESTRINGIDO_NUEVO",
                    "Talento Humano subió un documento a tu expediente",
                    categoria.getNombre() + ": " + titulo, null);
        }
        return documento;
    }

    @Transactional(readOnly = true)
    public List<Documento> carpetaRestringidaDe(String usuarioId) {
        if (!capacidades.puedeSobre(Capacidades.DOCUMENTO_RESTRINGIDO_VER, usuarioId)) {
            throw new PermisoDocumentoException("No puedes ver los documentos restringidos de esa persona.");
        }
        return documentoRepository.buscarDeLaCarpeta(usuarioId, ClaseDocumento.RESTRINGIDO);
    }

    /**
     * La última versión de un documento, lista para bajarla. Sirve tanto para la
     * pública (el dueño la pide) como, más adelante, para la restringida que RH
     * consulte: el permiso lo decide la capacidad de VER que corresponda a la clase.
     */
    @Transactional(readOnly = true)
    public ArchivoDescargado descargar(String documentoId) {
        Documento documento = documentoRepository.buscarCompleto(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("El documento " + documentoId + " no existe."));

        String capacidadDeVer = documento.esRestringido()
                ? Capacidades.DOCUMENTO_RESTRINGIDO_VER
                : Capacidades.DOCUMENTO_PUBLICO_SUBIR;
        if (!capacidades.puedeSobre(capacidadDeVer, documento.getUsuario().getId())) {
            throw new PermisoDocumentoException("No puedes ver este documento.");
        }

        DocumentoVersion version = versionRepository
                .findByDocumentoIdAndNumero(documentoId, documento.getVersionActual())
                .orElseThrow(() -> new IllegalStateException(
                        "El documento " + documentoId + " no tiene ninguna versión subida."));

        return new ArchivoDescargado(
                almacenamiento.descargar(version.getAlmacenamientoClave()),
                version.getNombreArchivo(), version.getMime(), version.getTamanoBytes());
    }

    @Transactional
    public Documento subirNuevaVersion(Usuario actor, String documentoId, MultipartFile archivo) {
        Documento documento = documentoRepository.buscarCompleto(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("El documento " + documentoId + " no existe."));
        exigirPuedeAdministrar(documento);

        DocumentoVersion version = nuevaVersion(documento, actor, archivo);
        documento.setVersionActual(version.getNumero());
        documentoRepository.save(documento);
        return documento;
    }

    @Transactional
    public void editarMetadatos(Usuario actor, String documentoId, String titulo, String descripcion) {
        Documento documento = documentoRepository.buscarCompleto(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("El documento " + documentoId + " no existe."));
        exigirPuedeAdministrar(documento);

        documento.setTitulo(titulo);
        documento.setDescripcion(descripcion);
        documentoRepository.save(documento);
    }

    /**
     * "Borra" el documento: lo archiva. Las versiones y los archivos en disco se
     * conservan —es expediente laboral, el histórico no se destruye—, simplemente deja
     * de aparecer en la carpeta.
     */
    @Transactional
    public void eliminar(Usuario actor, String documentoId) {
        Documento documento = documentoRepository.buscarCompleto(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("El documento " + documentoId + " no existe."));
        exigirPuedeAdministrar(documento);

        documento.setArchivado(true);
        documentoRepository.save(documento);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void exigirPuedeAdministrar(Documento documento) {
        if (documento.esRestringido()) {
            exigirCapacidadRestringidos();
            return;
        }
        // Público: el dueño administra el suyo; quien tenga alcance TODOS (RH/ADMIN)
        // también puede, por si RH necesita corregir algo a nombre del colaborador.
        if (!capacidades.puedeSobre(Capacidades.DOCUMENTO_PUBLICO_SUBIR, documento.getUsuario().getId())) {
            throw new PermisoDocumentoException("No puedes administrar este documento.");
        }
    }

    private void exigirCapacidadRestringidos() {
        if (!capacidades.puede(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR)) {
            throw new PermisoDocumentoException(
                    "Solo Talento Humano o un administrador pueden administrar documentos restringidos.");
        }
    }

    private DocumentoCategoria categoriaDe(String codigo, ClaseDocumento claseEsperada) {
        DocumentoCategoria categoria = categoriaRepository.findById(codigo)
                .orElseThrow(() -> new IllegalArgumentException("La categoría " + codigo + " no existe."));
        if (categoria.getClase() != claseEsperada) {
            throw new IllegalArgumentException(
                    "La categoría " + codigo + " es " + categoria.getClase() + ", no " + claseEsperada + ".");
        }
        return categoria;
    }

    private Documento crearDocumentoConPrimeraVersion(Usuario dueño, Usuario actor, DocumentoCategoria categoria,
                                                       String titulo, String descripcion, MultipartFile archivo) {
        Documento documento = new Documento();
        documento.setId(UuidV7.nuevoId());
        documento.setUsuario(dueño);
        documento.setCategoria(categoria);
        documento.setTitulo(titulo);
        documento.setDescripcion(descripcion);
        documento.setCreadoPor(actor);
        documento.setVersionActual(0);
        documentoRepository.save(documento);

        DocumentoVersion version = nuevaVersion(documento, actor, archivo);
        documento.setVersionActual(version.getNumero());
        documentoRepository.save(documento);
        return documento;
    }

    private DocumentoVersion nuevaVersion(Documento documento, Usuario actor, MultipartFile archivo) {
        PoliticaArchivos.ArchivoValidado validado = PoliticaArchivos.validar(archivo, TipoArchivo.DOCUMENTO);
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo enviado.", e);
        }

        int numero = documento.getVersionActual() + 1;
        String clave = validado.clave();

        almacenamiento.subir(clave, new ByteArrayInputStream(contenido), contenido.length, validado.mime());

        DocumentoVersion version = new DocumentoVersion();
        version.setId(UuidV7.nuevoId());
        version.setDocumento(documento);
        version.setNumero(numero);
        version.setNombreArchivo(PoliticaArchivos.nombreParaMostrar(archivo.getOriginalFilename()));
        version.setMime(validado.mime());
        version.setTamanoBytes(contenido.length);
        version.setAlmacenamientoClave(clave);
        version.setHashSha256(sha256(contenido));
        version.setSubidoPor(actor);
        return versionRepository.save(version);
    }

    private String sha256(byte[] contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }
}
