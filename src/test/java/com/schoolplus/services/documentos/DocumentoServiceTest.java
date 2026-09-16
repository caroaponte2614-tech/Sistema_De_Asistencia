package com.schoolplus.services.documentos;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.Documento;
import com.schoolplus.model.DocumentoCategoria;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.DocumentoCategoriaRepository;
import com.schoolplus.repository.DocumentoRepository;
import com.schoolplus.repository.DocumentoVersionRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reglas de las dos carpetas de documentos (§4.2, D-017): pública la administra el
 * dueño, restringida solo quien tenga {@code DOCUMENTO_RESTRINGIDO_SUBIR} (ADMIN y RH).
 * El almacenamiento se dobla con un fake en memoria: no hace falta disco para probar
 * quién puede hacer qué.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private DocumentoVersionRepository versionRepository;
    @Mock
    private DocumentoCategoriaRepository categoriaRepository;
    @Mock
    private CapacidadService capacidades;
    @Mock
    private NotificacionService notificacionService;

    private DocumentoAlmacenamiento almacenamientoFake;
    private DocumentoService documentos;

    @BeforeEach
    void crearService() {
        almacenamientoFake = new DocumentoAlmacenamientoEnMemoria();
        documentos = new DocumentoService(documentoRepository, versionRepository, categoriaRepository,
                almacenamientoFake, capacidades, notificacionService);

        when(documentoRepository.save(any(Documento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void elDueñoSubeSuPropioDocumentoPublico() {
        when(capacidades.puede(Capacidades.DOCUMENTO_PUBLICO_SUBIR)).thenReturn(true);
        when(categoriaRepository.findById("HOJA_VIDA")).thenReturn(Optional.of(categoria("HOJA_VIDA", ClaseDocumento.PUBLICO)));

        Usuario pedro = usuario("col-1", "Pedro");
        Documento documento = documentos.subirPublico(pedro, "HOJA_VIDA", "Mi hoja de vida", null, archivo());

        assertThat(documento.getUsuario()).isEqualTo(pedro);
        assertThat(documento.getVersionActual()).isEqualTo(1);
    }

    @Test
    void sinCapacidadNoSePuedeSubirUnDocumentoPublico() {
        when(capacidades.puede(Capacidades.DOCUMENTO_PUBLICO_SUBIR)).thenReturn(false);

        assertThatThrownBy(() -> documentos.subirPublico(usuario("col-1", "Pedro"), "HOJA_VIDA", "t", null, archivo()))
                .isInstanceOf(PermisoDocumentoException.class);
    }

    @Test
    void noSePuedeSubirUnRestringidoComoSiFueraPublico() {
        when(capacidades.puede(Capacidades.DOCUMENTO_PUBLICO_SUBIR)).thenReturn(true);
        when(categoriaRepository.findById("CONTRATO")).thenReturn(Optional.of(categoria("CONTRATO", ClaseDocumento.RESTRINGIDO)));

        assertThatThrownBy(() -> documentos.subirPublico(usuario("col-1", "Pedro"), "CONTRATO", "t", null, archivo()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rhSubeUnRestringidoParaOtroColaborador() {
        when(capacidades.puede(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR)).thenReturn(true);
        when(categoriaRepository.findById("CONTRATO")).thenReturn(Optional.of(categoria("CONTRATO", ClaseDocumento.RESTRINGIDO)));

        Usuario rh = usuario("rh-1", "Talento Humano");
        Usuario pedro = usuario("col-1", "Pedro");

        Documento documento = documentos.subirRestringido(rh, pedro, "CONTRATO", "Contrato", null, archivo());

        assertThat(documento.getUsuario()).isEqualTo(pedro);
        assertThat(documento.getCreadoPor()).isEqualTo(rh);
    }

    @Test
    void unColaboradorSinLaCapacidadNoPuedeSubirRestringidos() {
        when(capacidades.puede(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR)).thenReturn(false);

        assertThatThrownBy(() -> documentos.subirRestringido(
                usuario("col-1", "Pedro"), usuario("col-2", "Ana"), "CONTRATO", "t", null, archivo()))
                .isInstanceOf(PermisoDocumentoException.class);
    }

    @Test
    void eliminarUnRestringidoExigeLaCapacidadDeRh() {
        Documento documento = documentoRestringidoExistente();
        when(documentoRepository.buscarCompleto("doc-1")).thenReturn(Optional.of(documento));
        when(capacidades.puede(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR)).thenReturn(false);

        assertThatThrownBy(() -> documentos.eliminar(usuario("col-1", "Pedro"), "doc-1"))
                .isInstanceOf(PermisoDocumentoException.class);
    }

    @Test
    void eliminarSoloArchivaNoBorraElHistorico() {
        Documento documento = documentoRestringidoExistente();
        when(documentoRepository.buscarCompleto("doc-1")).thenReturn(Optional.of(documento));
        when(capacidades.puede(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR)).thenReturn(true);

        documentos.eliminar(usuario("rh-1", "RH"), "doc-1");

        assertThat(documento.isArchivado()).isTrue();
    }

    private Documento documentoRestringidoExistente() {
        Documento documento = new Documento();
        documento.setId("doc-1");
        documento.setUsuario(usuario("col-1", "Pedro"));
        documento.setCategoria(categoria("CONTRATO", ClaseDocumento.RESTRINGIDO));
        return documento;
    }

    private DocumentoCategoria categoria(String codigo, ClaseDocumento clase) {
        DocumentoCategoria categoria = new DocumentoCategoria();
        categoria.setCodigo(codigo);
        categoria.setNombre(codigo);
        categoria.setClase(clase);
        return categoria;
    }

    private Usuario usuario(String id, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre(nombre);
        return usuario;
    }

    private MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "hoja-de-vida.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes());
    }

    /** Doble en memoria de {@link DocumentoAlmacenamiento}: nada de disco real. */
    private static final class DocumentoAlmacenamientoEnMemoria implements DocumentoAlmacenamiento {
        @Override
        public void subir(String clave, java.io.InputStream contenido, long tamanoBytes, String mime) {
            // No hace falta guardar nada: los tests solo comprueban las reglas de permiso.
        }

        @Override
        public java.io.InputStream descargar(String clave) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream descargar(String clave, Long offset, Long length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.schoolplus.services.documentos.InfoObjeto info(String clave) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void eliminar(String clave) {
            // no-op
        }
    }
}
