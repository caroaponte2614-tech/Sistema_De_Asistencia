package com.schoolplus.services.certificados;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.CertificadoLaboral;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CertificadoLaboralRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Certificado laboral: autoservicio inmediato, y que la verificación pública enmascare los datos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CertificadoLaboralServiceTest {

    @Mock
    private CertificadoLaboralRepository certificadoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DocumentoAlmacenamiento almacenamiento;
    @Mock
    private CapacidadService capacidadService;
    @Mock
    private CertificadoLaboralPdfBuilder pdfBuilder;

    private CertificadoLaboralService service;

    private Usuario colaborador;

    @BeforeEach
    void preparar() {
        service = new CertificadoLaboralService(certificadoRepository, usuarioRepository, almacenamiento,
                capacidadService, pdfBuilder, "http://localhost:8090");

        colaborador = new Usuario();
        colaborador.setId("colab-1");
        colaborador.setEmail("colaborador@schoolplus.example");
        colaborador.setNombre("Ana María Pérez");
        colaborador.setCedula("1234567890");

        lenient().when(usuarioRepository.findById("colab-1")).thenReturn(Optional.of(colaborador));
        lenient().when(certificadoRepository.findByCodigoVerificacion(any())).thenReturn(Optional.empty());
        lenient().when(certificadoRepository.save(any(CertificadoLaboral.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pdfBuilder.generar(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void generarFallaSinPermisoSobreLaPersona() {
        when(capacidadService.puedeSobre(Capacidades.CERTIFICADO_LABORAL_SOLICITAR, "colab-1")).thenReturn(false);
        when(capacidadService.puedeSobre(Capacidades.CERTIFICADO_LABORAL_GENERAR_TERCEROS, "colab-1")).thenReturn(false);

        assertThatThrownBy(() -> service.generar(colaborador, "colab-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(certificadoRepository, never()).save(any());
    }

    @Test
    void generarSubeElPdfYGuardaElRegistro() {
        when(capacidadService.puedeSobre(Capacidades.CERTIFICADO_LABORAL_SOLICITAR, "colab-1")).thenReturn(true);

        CertificadoLaboral certificado = service.generar(colaborador, "colab-1");

        assertThat(certificado.getUsuario()).isSameAs(colaborador);
        assertThat(certificado.getCodigoVerificacion()).hasSize(10);
        assertThat(certificado.getPdfClave()).startsWith("certificados/").endsWith(".pdf");
        verify(almacenamiento).subir(any(), any(), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    void verificarDevuelveVacioSiElCodigoNoExiste() {
        when(certificadoRepository.findByCodigoVerificacion("XXXX")).thenReturn(Optional.empty());

        assertThat(service.verificar("XXXX")).isEmpty();
    }

    @Test
    void verificarEnmascaraNombreYCedula() {
        CertificadoLaboral certificado = new CertificadoLaboral();
        certificado.setId("cert-1");
        certificado.setUsuario(colaborador);
        certificado.setCodigoVerificacion("ABC123XYZ0");
        when(certificadoRepository.findByCodigoVerificacion("ABC123XYZ0")).thenReturn(Optional.of(certificado));

        CertificadoVerificacion resultado = service.verificar("ABC123XYZ0").orElseThrow();

        assertThat(resultado.nombreEnmascarado()).isEqualTo("A*** M*** P***");
        assertThat(resultado.cedulaEnmascarada()).isEqualTo("****7890");
    }
}
