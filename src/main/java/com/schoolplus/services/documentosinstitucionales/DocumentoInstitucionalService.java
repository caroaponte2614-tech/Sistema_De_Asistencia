package com.schoolplus.services.documentosinstitucionales;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.DocumentoInstitucional;
import com.schoolplus.model.DocumentoInstitucionalVista;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.DocumentoInstitucionalRepository;
import com.schoolplus.repository.DocumentoInstitucionalVistaRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.documentos.PoliticaArchivos;
import com.schoolplus.services.documentos.PoliticaArchivos.ArchivoValidado;
import com.schoolplus.services.documentos.TipoArchivo;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Documentos institucionales: manuales, políticas y formatos que ve toda la empresa —
 * distinto de "mis documentos" (portafolio personal de cada quien, {@code Documento}).
 *
 * <p>Talento Humano administra el catálogo directamente, sin aprobación (mismo espíritu
 * que {@code CursoService}). Cada apertura queda registrada: "verlo" ES "marcarlo
 * leído", no hay un botón aparte para eso.</p>
 */
@Service
public class DocumentoInstitucionalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoInstitucionalService.class);

    private final DocumentoInstitucionalRepository documentoRepository;
    private final DocumentoInstitucionalVistaRepository vistaRepository;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;

    public DocumentoInstitucionalService(DocumentoInstitucionalRepository documentoRepository,
                                         DocumentoInstitucionalVistaRepository vistaRepository,
                                         DocumentoAlmacenamiento almacenamiento,
                                         CapacidadService capacidades) {
        this.documentoRepository = documentoRepository;
        this.vistaRepository = vistaRepository;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
    }

    // ── Lectura (cualquiera con DOCUMENTO_INSTITUCIONAL_VER) ─────────────────

    @Transactional(readOnly = true)
    public List<DocumentoInstitucional> publicados() {
        return documentoRepository.findByPublicadoTrueAndArchivadoFalseOrderByOrdenAscCreadoEnDesc();
    }

    @Transactional(readOnly = true)
    public Set<String> vistosPor(Usuario actor) {
        return vistaRepository.documentosVistosPor(actor.getId());
    }

    @Transactional(readOnly = true)
    public ArchivoDescargado descargar(String documentoId) {
        DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
        return new ArchivoDescargado(
                almacenamiento.descargar(documento.getArchivoClave()),
                documento.getTitulo(), documento.getArchivoMime(),
                documento.getArchivoTamanoBytes() == null ? 0 : documento.getArchivoTamanoBytes());
    }

    /** Se llama al abrir el documento, no con un botón aparte: abrirlo es leerlo. */
    @Transactional
    public void marcarVisto(Usuario actor, String documentoId) {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoInstitucionalVista vista = vistaRepository
                .findByDocumentoIdAndUsuarioId(documentoId, actor.getId())
                .orElseGet(() -> {
                    DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                            .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
                    DocumentoInstitucionalVista nueva = new DocumentoInstitucionalVista();
                    nueva.setId(UuidV7.nuevoId());
                    nueva.setDocumento(documento);
                    nueva.setUsuario(actor);
                    nueva.setPrimeraVezEn(ahora);
                    nueva.setVecesVisto(0);
                    return nueva;
                });
        vista.setUltimaVezEn(ahora);
        vista.setVecesVisto(vista.getVecesVisto() + 1);
        vistaRepository.save(vista);
    }

    // ── Administración (Talento Humano) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentoInstitucional> listarTodos() {
        exigirCapacidadAdministrar();
        return documentoRepository.findByArchivadoFalseOrderByCreadoEnDesc();
    }

    @Transactional(readOnly = true)
    public DocumentoInstitucional obtenerParaAdministrar(String documentoId) {
        exigirCapacidadAdministrar();
        return documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
    }

    /** Miniatura de la tarjeta (listado y carrusel del portal). Sin miniatura, 404. */
    @Transactional(readOnly = true)
    public ArchivoDescargado miniatura(String documentoId) {
        DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
        if (documento.getMiniaturaClave() == null) {
            throw new IllegalArgumentException("Ese documento no tiene miniatura.");
        }
        var info = almacenamiento.info(documento.getMiniaturaClave());
        return new ArchivoDescargado(almacenamiento.descargar(documento.getMiniaturaClave()),
                "miniatura", info.mime(), info.tamanoBytes());
    }

    @Transactional
    public DocumentoInstitucional subir(Usuario actor, String titulo, String descripcion, String categoria,
                                        boolean obligatorio, MultipartFile archivo) {
        return subir(actor, titulo, descripcion, categoria, obligatorio, archivo, null);
    }

    @Transactional
    public DocumentoInstitucional subir(Usuario actor, String titulo, String descripcion, String categoria,
                                        boolean obligatorio, MultipartFile archivo, MultipartFile miniatura) {
        exigirCapacidadAdministrar();
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El documento necesita un título.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Elige un archivo antes de subirlo.");
        }

        DocumentoInstitucional documento = new DocumentoInstitucional();
        documento.setId(UuidV7.nuevoId());
        documento.setTitulo(titulo);
        documento.setDescripcion(Textos.vacioANulo(descripcion));
        documento.setCategoria(Textos.vacioANulo(categoria));
        documento.setObligatorio(obligatorio);
        documento.setCreadoPor(actor);
        documento.setPublicado(false);
        subirArchivo(documento, archivo);
        if (miniatura != null && !miniatura.isEmpty()) {
            subirMiniatura(documento, miniatura);
        }

        documentoRepository.save(documento);
        log.info("Documento institucional creado: {} ({}) por {}", titulo, documento.getId(), actor.getEmail());
        return documento;
    }

    @Transactional
    public DocumentoInstitucional actualizar(Usuario actor, String documentoId, String titulo, String descripcion,
                                             String categoria, boolean obligatorio,
                                             MultipartFile nuevoArchivo, MultipartFile nuevaMiniatura) {
        exigirCapacidadAdministrar();
        DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));

        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El documento necesita un título.");
        }
        documento.setTitulo(titulo);
        documento.setDescripcion(Textos.vacioANulo(descripcion));
        documento.setCategoria(Textos.vacioANulo(categoria));
        documento.setObligatorio(obligatorio);

        if (nuevoArchivo != null && !nuevoArchivo.isEmpty()) {
            subirArchivo(documento, nuevoArchivo);
        }
        if (nuevaMiniatura != null && !nuevaMiniatura.isEmpty()) {
            subirMiniatura(documento, nuevaMiniatura);
        }

        documentoRepository.save(documento);
        log.info("Documento institucional {} actualizado por {}", documentoId, actor.getEmail());
        return documento;
    }

    @Transactional
    public DocumentoInstitucional publicar(Usuario actor, String documentoId, boolean publicado) {
        exigirCapacidadAdministrar();
        DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
        documento.setPublicado(publicado);
        documentoRepository.save(documento);
        log.info("Documento institucional {} {} por {}", documentoId,
                publicado ? "publicado" : "despublicado", actor.getEmail());
        return documento;
    }

    @Transactional
    public void archivar(Usuario actor, String documentoId) {
        exigirCapacidadAdministrar();
        DocumentoInstitucional documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese documento no existe."));
        documento.setArchivado(true);
        documentoRepository.save(documento);
        log.info("Documento institucional {} archivado por {}", documentoId, actor.getEmail());
    }

    /** Sube o baja un documento un puesto en el orden del carrusel — sin drag-and-drop. */
    @Transactional
    public void mover(Usuario actor, String documentoId, boolean arriba) {
        exigirCapacidadAdministrar();
        List<DocumentoInstitucional> todos = new java.util.ArrayList<>(documentoRepository.findByArchivadoFalseOrderByCreadoEnDesc());
        normalizarOrden(todos);

        int indice = -1;
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).getId().equals(documentoId)) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            throw new IllegalArgumentException("Ese documento no existe.");
        }
        int destino = arriba ? indice - 1 : indice + 1;
        if (destino < 0 || destino >= todos.size()) {
            return;
        }

        int ordenActual = todos.get(indice).getOrden();
        int ordenDestino = todos.get(destino).getOrden();
        todos.get(indice).setOrden(ordenDestino);
        todos.get(destino).setOrden(ordenActual);
        documentoRepository.save(todos.get(indice));
        documentoRepository.save(todos.get(destino));
    }

    private void normalizarOrden(List<DocumentoInstitucional> documentos) {
        boolean necesitaOrden = documentos.stream().anyMatch(d -> d.getOrden() == null);
        if (!necesitaOrden) {
            documentos.sort((a, b) -> Integer.compare(a.getOrden(), b.getOrden()));
            return;
        }
        for (int i = 0; i < documentos.size(); i++) {
            documentos.get(i).setOrden(i);
            documentoRepository.save(documentos.get(i));
        }
    }

    private void subirArchivo(DocumentoInstitucional documento, MultipartFile archivo) {
        ArchivoValidado validado = PoliticaArchivos.validar(archivo, TipoArchivo.INSTITUCIONAL);
        try {
            almacenamiento.subir(validado.clave(), archivo.getInputStream(), archivo.getSize(), validado.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo enviado.", e);
        }
        documento.setArchivoClave(validado.clave());
        documento.setArchivoMime(validado.mime());
        documento.setArchivoTamanoBytes(archivo.getSize());
    }

    private void subirMiniatura(DocumentoInstitucional documento, MultipartFile miniatura) {
        ArchivoValidado validado = PoliticaArchivos.validar(miniatura, TipoArchivo.IMAGEN);
        try {
            almacenamiento.subir(validado.clave(), miniatura.getInputStream(), miniatura.getSize(), validado.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen enviada.", e);
        }
        documento.setMiniaturaClave(validado.clave());
    }

    private void exigirCapacidadAdministrar() {
        if (!capacidades.puede(Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR)) {
            throw new IllegalArgumentException("No tienes permiso para administrar documentos institucionales.");
        }
    }
}
