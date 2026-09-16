package com.schoolplus.services.documentosinstitucionales;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.DocumentoInstitucional;
import com.schoolplus.model.DocumentoInstitucionalVista;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.DocumentoInstitucionalRepository;
import com.schoolplus.repository.DocumentoInstitucionalVistaRepository;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Documentos institucionales: alta sin aprobación, y que abrir uno cuenta como leerlo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentoInstitucionalServiceTest {

    @Mock
    private DocumentoInstitucionalRepository documentoRepository;
    @Mock
    private DocumentoInstitucionalVistaRepository vistaRepository;
    @Mock
    private DocumentoAlmacenamiento almacenamiento;
    @Mock
    private CapacidadService capacidadService;

    private DocumentoInstitucionalService service;

    private Usuario rrhh;
    private Usuario colaborador;

    @BeforeEach
    void preparar() {
        service = new DocumentoInstitucionalService(documentoRepository, vistaRepository, almacenamiento, capacidadService);

        rrhh = new Usuario();
        rrhh.setId("rrhh-1");
        rrhh.setEmail("rrhh@schoolplus.example");

        colaborador = new Usuario();
        colaborador.setId("colab-1");
        colaborador.setEmail("colaborador@schoolplus.example");

        lenient().when(documentoRepository.save(any(DocumentoInstitucional.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(vistaRepository.save(any(DocumentoInstitucionalVista.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void subirFallaSinCapacidadDeAdministrar() {
        when(capacidadService.puede(Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR)).thenReturn(false);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "manual.pdf", "application/pdf", "%PDF-".getBytes());

        assertThatThrownBy(() -> service.subir(rrhh, "Manual", null, "Políticas", true, archivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permiso");

        verify(documentoRepository, never()).save(any());
    }

    @Test
    void subirGuardaSinPublicarYSubeElArchivo() {
        when(capacidadService.puede(Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR)).thenReturn(true);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "manual.pdf", "application/pdf", "%PDF-".getBytes());

        DocumentoInstitucional creado = service.subir(rrhh, "Manual de convivencia", "Reglas básicas", "Políticas", true, archivo);

        assertThat(creado.getTitulo()).isEqualTo("Manual de convivencia");
        assertThat(creado.isObligatorio()).isTrue();
        assertThat(creado.isPublicado()).isFalse();
        assertThat(creado.getArchivoClave()).startsWith("institucionales/").endsWith(".pdf").doesNotContain("manual");
        verify(almacenamiento).subir(any(), any(), org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    void marcarVistoCreaLaVistaLaPrimeraVez() {
        DocumentoInstitucional documento = new DocumentoInstitucional();
        documento.setId("doc-1");
        when(vistaRepository.findByDocumentoIdAndUsuarioId("doc-1", "colab-1")).thenReturn(Optional.empty());
        when(documentoRepository.findById("doc-1")).thenReturn(Optional.of(documento));

        service.marcarVisto(colaborador, "doc-1");

        var captor = org.mockito.ArgumentCaptor.forClass(DocumentoInstitucionalVista.class);
        verify(vistaRepository).save(captor.capture());
        assertThat(captor.getValue().getVecesVisto()).isEqualTo(1);
        assertThat(captor.getValue().getPrimeraVezEn()).isNotNull();
        assertThat(captor.getValue().getUltimaVezEn()).isNotNull();
    }

    @Test
    void marcarVistoIncrementaVecesVistoCuandoYaExiste() {
        DocumentoInstitucionalVista existente = new DocumentoInstitucionalVista();
        existente.setId("vista-1");
        existente.setPrimeraVezEn(LocalDateTime.now().minusDays(1));
        existente.setVecesVisto(2);
        when(vistaRepository.findByDocumentoIdAndUsuarioId("doc-1", "colab-1")).thenReturn(Optional.of(existente));

        service.marcarVisto(colaborador, "doc-1");

        assertThat(existente.getVecesVisto()).isEqualTo(3);
    }

    @Test
    void moverIntercambiaElOrdenConElVecino() {
        when(capacidadService.puede(Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR)).thenReturn(true);

        DocumentoInstitucional primero = documento("d1", 0);
        DocumentoInstitucional segundo = documento("d2", 1);
        when(documentoRepository.findByArchivadoFalseOrderByCreadoEnDesc()).thenReturn(List.of(primero, segundo));

        service.mover(rrhh, "d2", true);

        assertThat(primero.getOrden()).isEqualTo(1);
        assertThat(segundo.getOrden()).isEqualTo(0);
    }

    private DocumentoInstitucional documento(String id, int orden) {
        DocumentoInstitucional documento = new DocumentoInstitucional();
        documento.setId(id);
        documento.setOrden(orden);
        return documento;
    }
}
