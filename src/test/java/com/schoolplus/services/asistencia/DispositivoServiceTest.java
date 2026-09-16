package com.schoolplus.services.asistencia;

import com.schoolplus.model.Ambito;
import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoDispositivo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AsistenciaDispositivoRepository;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La vinculación de celulares, que es lo que impide que un estudiante registre a otro.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispositivoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-15T13:00:00Z");
    private static final String CELULAR = "cookie-del-celular-de-ana";
    private static final String OTRO_CELULAR = "cookie-de-otro-celular";

    @Mock
    private AsistenciaDispositivoRepository dispositivoRepository;
    @Mock
    private ClaseInscripcionRepository inscripcionRepository;
    @Mock
    private CapacidadService capacidades;

    private DispositivoService servicio;
    private Usuario ana;
    private Usuario luis;
    private Usuario profesor;

    @BeforeEach
    void preparar() {
        servicio = new DispositivoService(dispositivoRepository, inscripcionRepository, capacidades,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        ana = usuario("est-ana", "ana@schoolplus.example");
        luis = usuario("est-luis", "luis@schoolplus.example");
        profesor = usuario("doc-1", "profe@schoolplus.example");

        when(dispositivoRepository.save(any(AsistenciaDispositivo.class))).thenAnswer(i -> i.getArgument(0));
        when(dispositivoRepository.saveAndFlush(any(AsistenciaDispositivo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void elPrimerCelularQuedaVinculadoSinPedirPermiso() {
        when(dispositivoRepository.findByActivoEstudianteId("est-ana")).thenReturn(Optional.empty());
        when(dispositivoRepository.findByActivoIdentificador(any())).thenReturn(Optional.empty());

        AsistenciaDispositivo vinculado = servicio.autorizarParaRegistro(ana, CELULAR, "Android Chrome");

        assertThat(vinculado.getEstado()).isEqualTo(EstadoDispositivo.ACTIVO);
        assertThat(vinculado.getActivoEstudianteId()).isEqualTo("est-ana");
        assertThat(vinculado.getActivoIdentificador()).isEqualTo(vinculado.getIdentificadorHash());
        // La cookie en claro nunca se guarda: en la tabla va su hash.
        assertThat(vinculado.getIdentificadorHash()).isNotEqualTo(CELULAR).hasSize(64);
    }

    @Test
    void elMismoCelularSigueValiendoSinCrearNada() {
        AsistenciaDispositivo activo = activoDe(ana, CELULAR);
        when(dispositivoRepository.findByActivoEstudianteId("est-ana")).thenReturn(Optional.of(activo));

        AsistenciaDispositivo resultado = servicio.autorizarParaRegistro(ana, CELULAR, "Android Chrome");

        assertThat(resultado).isSameAs(activo);
        verify(dispositivoRepository, never()).save(any());
    }

    @Test
    void desdeOtroCelularSeRechazaYQuedaLaSolicitud() {
        when(dispositivoRepository.findByActivoEstudianteId("est-ana"))
                .thenReturn(Optional.of(activoDe(ana, CELULAR)));
        when(dispositivoRepository.findFirstByEstudianteIdAndIdentificadorHashAndEstado(
                any(), any(), any())).thenReturn(Optional.empty());

        String userAgentReal = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

        assertThatThrownBy(() -> servicio.autorizarParaRegistro(ana, OTRO_CELULAR, userAgentReal))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.DISPOSITIVO_NO_AUTORIZADO);

        ArgumentCaptor<AsistenciaDispositivo> guardado = ArgumentCaptor.forClass(AsistenciaDispositivo.class);
        verify(dispositivoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getEstado()).isEqualTo(EstadoDispositivo.PENDIENTE);
        assertThat(guardado.getValue().getDescripcion()).isEqualTo("iPhone/iPad · Safari");
    }

    @Test
    void noSePideDosVecesLaMismaAutorización() {
        when(dispositivoRepository.findByActivoEstudianteId("est-ana"))
                .thenReturn(Optional.of(activoDe(ana, CELULAR)));
        when(dispositivoRepository.findFirstByEstudianteIdAndIdentificadorHashAndEstado(
                any(), any(), any())).thenReturn(Optional.of(new AsistenciaDispositivo()));

        assertThatThrownBy(() -> servicio.autorizarParaRegistro(ana, OTRO_CELULAR, "iPhone"))
                .isInstanceOf(RegistroRechazadoException.class);

        verify(dispositivoRepository, never()).save(any());
    }

    @Test
    void elCelularDeOtroEstudianteNoSirve() {
        when(dispositivoRepository.findByActivoEstudianteId("est-ana")).thenReturn(Optional.empty());
        when(dispositivoRepository.findByActivoIdentificador(any()))
                .thenReturn(Optional.of(activoDe(luis, CELULAR)));

        assertThatThrownBy(() -> servicio.autorizarParaRegistro(ana, CELULAR, "Android"))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.DISPOSITIVO_DE_OTRO_ESTUDIANTE);
    }

    @Test
    void sinCookieNoSePuedeIdentificarElCelular() {
        assertThatThrownBy(() -> servicio.autorizarParaRegistro(ana, null, "Android"))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.DISPOSITIVO_NO_IDENTIFICADO);
    }

    // ── Autorización del cambio ──────────────────────────────────────────────

    @Test
    void elProfesorDeEseEstudianteAprueba() {
        AsistenciaDispositivo anterior = activoDe(ana, CELULAR);
        AsistenciaDispositivo solicitud = pendienteDe(ana, OTRO_CELULAR);
        when(dispositivoRepository.buscarConEstudiante("disp-nuevo")).thenReturn(Optional.of(solicitud));
        when(dispositivoRepository.findByActivoIdentificador(solicitud.getIdentificadorHash()))
                .thenReturn(Optional.empty());
        when(dispositivoRepository.findByActivoEstudianteId("est-ana")).thenReturn(Optional.of(anterior));
        when(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)).thenReturn(Optional.of(Ambito.PROPIO));
        when(inscripcionRepository.existsByEstudianteIdAndClaseProfesorIdAndActivaTrue("est-ana", "doc-1"))
                .thenReturn(true);

        AsistenciaDispositivo aprobado = servicio.aprobarCambio(profesor, "disp-nuevo");

        assertThat(aprobado.getEstado()).isEqualTo(EstadoDispositivo.ACTIVO);
        assertThat(aprobado.getResueltoPor()).isSameAs(profesor);
        // El anterior suelta los candados únicos antes de que el nuevo los tome.
        assertThat(anterior.getEstado()).isEqualTo(EstadoDispositivo.REEMPLAZADO);
        assertThat(anterior.getActivoEstudianteId()).isNull();
        assertThat(anterior.getActivoIdentificador()).isNull();
    }

    @Test
    void unProfesorAjenoNoAprueba() {
        when(dispositivoRepository.buscarConEstudiante("disp-nuevo"))
                .thenReturn(Optional.of(pendienteDe(ana, OTRO_CELULAR)));
        when(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)).thenReturn(Optional.of(Ambito.PROPIO));
        when(inscripcionRepository.existsByEstudianteIdAndClaseProfesorIdAndActivaTrue("est-ana", "doc-1"))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.aprobarCambio(profesor, "disp-nuevo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está en ninguna de tus clases");
    }

    @Test
    void noSePuedeAprobarUnCelularQueYaEsDeOtro() {
        AsistenciaDispositivo solicitud = pendienteDe(ana, OTRO_CELULAR);
        when(dispositivoRepository.buscarConEstudiante("disp-nuevo")).thenReturn(Optional.of(solicitud));
        when(dispositivoRepository.findByActivoIdentificador(solicitud.getIdentificadorHash()))
                .thenReturn(Optional.of(activoDe(luis, OTRO_CELULAR)));
        when(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)).thenReturn(Optional.of(Ambito.TODOS));

        assertThatThrownBy(() -> servicio.aprobarCambio(profesor, "disp-nuevo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vinculado a otro estudiante");
    }

    @Test
    void sinLaCapacidadNoSeAutorizaNada() {
        when(dispositivoRepository.buscarConEstudiante("disp-nuevo"))
                .thenReturn(Optional.of(pendienteDe(ana, OTRO_CELULAR)));
        when(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobarCambio(profesor, "disp-nuevo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Ayudas ───────────────────────────────────────────────────────────────

    private Usuario usuario(String id, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre(email.split("@")[0]);
        usuario.setEmail(email);
        return usuario;
    }

    private AsistenciaDispositivo activoDe(Usuario estudiante, String cookie) {
        AsistenciaDispositivo dispositivo = pendienteDe(estudiante, cookie);
        dispositivo.activar(LocalDateTime.now(Clock.fixed(AHORA, ZoneOffset.UTC)));
        return dispositivo;
    }

    private AsistenciaDispositivo pendienteDe(Usuario estudiante, String cookie) {
        AsistenciaDispositivo dispositivo = new AsistenciaDispositivo();
        dispositivo.setId("disp-" + cookie.hashCode());
        dispositivo.setEstudiante(estudiante);
        dispositivo.setIdentificadorHash(DispositivoService.hashDe(cookie));
        dispositivo.setEstado(EstadoDispositivo.PENDIENTE);
        return dispositivo;
    }
}
