package com.schoolplus.services.perfilcolaborador;

import com.schoolplus.dto.HijoEntrada;
import com.schoolplus.dto.PerfilDetalle;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.PerfilColaborador;
import com.schoolplus.model.PerfilEvaluacion;
import com.schoolplus.model.PerfilHijo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.PerfilColaboradorRepository;
import com.schoolplus.repository.PerfilEvaluacionRepository;
import com.schoolplus.repository.PerfilHijoRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ficha del colaborador: quién puede ver/editar, y que las evaluaciones nunca se pisan. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerfilColaboradorServiceTest {

    private static final LocalDate HOY = LocalDate.now();

    @Mock
    private PerfilColaboradorRepository perfilRepository;
    @Mock
    private PerfilHijoRepository hijoRepository;
    @Mock
    private PerfilEvaluacionRepository evaluacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CapacidadService capacidadService;

    private PerfilColaboradorService service;

    private Usuario coordinador;
    private Usuario colaborador;

    @BeforeEach
    void preparar() {
        service = new PerfilColaboradorService(perfilRepository, hijoRepository, evaluacionRepository,
                usuarioRepository, capacidadService);

        coordinador = new Usuario();
        coordinador.setId("coord-1");
        coordinador.setEmail("coordinador@schoolplus.example");

        colaborador = new Usuario();
        colaborador.setId("colab-1");
        colaborador.setEmail("colaborador@schoolplus.example");

        lenient().when(usuarioRepository.findById("colab-1")).thenReturn(Optional.of(colaborador));
        lenient().when(perfilRepository.save(any(PerfilColaborador.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(hijoRepository.save(any(PerfilHijo.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(evaluacionRepository.save(any(PerfilEvaluacion.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void obtenerFallaSinPermisoDeVer() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_VER, "colab-1")).thenReturn(false);

        assertThatThrownBy(() -> service.obtener(coordinador, "colab-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Bug real encontrado en producción: un COORDINADOR solo tiene PERFIL_COLABORADOR_VER
     * en ámbito AREA (V20), y ese ámbito nunca incluye al propio actor (nadie es su propio
     * subordinado) — puedeSobre(VER, miPropioId) da false aunque el @PreAuthorize del
     * controlador ya haya confirmado que tiene la capacidad. Ver a uno mismo no debe pasar
     * por ese chequeo.
     */
    @Test
    void obtenerSePermiteAUnoMismoAunqueElAmbitoSeaArea() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_VER, "coord-1")).thenReturn(false);
        when(perfilRepository.findById("coord-1")).thenReturn(Optional.empty());
        when(usuarioRepository.findById("coord-1")).thenReturn(Optional.of(coordinador));
        when(hijoRepository.findByUsuarioIdOrderByOrdenAsc("coord-1")).thenReturn(List.of());
        when(evaluacionRepository.findByUsuarioIdOrderByFechaDesc("coord-1")).thenReturn(List.of());

        PerfilDetalle detalle = service.obtener(coordinador, "coord-1");

        assertThat(detalle.colaborador()).isSameAs(coordinador);
    }

    @Test
    void obtenerDevuelvePerfilNuloCuandoNuncaSeLleno() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_VER, "colab-1")).thenReturn(true);
        when(perfilRepository.findById("colab-1")).thenReturn(Optional.empty());
        when(hijoRepository.findByUsuarioIdOrderByOrdenAsc("colab-1")).thenReturn(List.of());
        when(evaluacionRepository.findByUsuarioIdOrderByFechaDesc("colab-1")).thenReturn(List.of());

        PerfilDetalle detalle = service.obtener(colaborador, "colab-1");

        assertThat(detalle.perfil()).isNull();
        assertThat(detalle.hijos()).isEmpty();
    }

    @Test
    void guardarDatosBasicosFallaSinPermisoDeAdministrar() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, "colab-1")).thenReturn(false);

        assertThatThrownBy(() -> service.guardarDatosBasicos(coordinador, "colab-1", "Mamá", "Madre", "3001234567"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(perfilRepository, never()).save(any());
    }

    @Test
    void reemplazarHijosBorraLosViejosAntesDeCrearLosNuevos() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, "colab-1")).thenReturn(true);

        service.reemplazarHijos(coordinador, "colab-1", List.of(
                new HijoEntrada("Juan", HOY.minusYears(5)),
                new HijoEntrada("Ana", HOY.minusYears(3))));

        verify(hijoRepository).deleteByUsuarioId("colab-1");
        verify(hijoRepository, org.mockito.Mockito.times(2)).save(any(PerfilHijo.class));
    }

    @Test
    void reemplazarHijosExigeNombreYFecha() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, "colab-1")).thenReturn(true);

        assertThatThrownBy(() -> service.reemplazarHijos(coordinador, "colab-1",
                List.of(new HijoEntrada("", HOY))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void agregarEvaluacionFallaConFechaFutura() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, "colab-1")).thenReturn(true);

        assertThatThrownBy(() -> service.agregarEvaluacion(coordinador, "colab-1", HOY.plusDays(1), "Bueno", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");

        verify(evaluacionRepository, never()).save(any());
    }

    @Test
    void agregarEvaluacionQuedaGuardadaSinPisarNadaMasViejo() {
        when(capacidadService.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, "colab-1")).thenReturn(true);

        PerfilEvaluacion evaluacion = service.agregarEvaluacion(coordinador, "colab-1", HOY, "Sobresaliente", "Buen semestre");

        assertThat(evaluacion.getUsuario()).isSameAs(colaborador);
        assertThat(evaluacion.getEvaluadoPor()).isSameAs(coordinador);
        assertThat(evaluacion.getCalificacion()).isEqualTo("Sobresaliente");
        verify(evaluacionRepository, never()).deleteAll();
        verify(evaluacionRepository, never()).delete(any());
    }
}
