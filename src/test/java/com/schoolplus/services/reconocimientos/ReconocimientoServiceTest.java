package com.schoolplus.services.reconocimientos;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.RegistroReconocimiento;
import com.schoolplus.model.TipoReconocimiento;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RegistroReconocimientoRepository;
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

/** Reconocimientos y llamados: sin aprobación, solo el chequeo de a quién puede registrar. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconocimientoServiceTest {

    private static final LocalDate HOY = LocalDate.now();

    @Mock
    private RegistroReconocimientoRepository registroRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DocumentoAlmacenamiento almacenamiento;
    @Mock
    private CapacidadService capacidadService;

    private ReconocimientoService service;

    private Usuario coordinador;
    private Usuario colaborador;

    @BeforeEach
    void preparar() {
        service = new ReconocimientoService(registroRepository, usuarioRepository, almacenamiento, capacidadService);

        coordinador = new Usuario();
        coordinador.setId("coord-1");
        coordinador.setEmail("coordinador@schoolplus.example");

        colaborador = new Usuario();
        colaborador.setId("colab-1");
        colaborador.setEmail("colaborador@schoolplus.example");
        colaborador.setNombre("Colaborador");

        lenient().when(usuarioRepository.findById("colab-1")).thenReturn(Optional.of(colaborador));
        lenient().when(registroRepository.save(any(RegistroReconocimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registrarFallaSiNoEstaACargo() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_CREAR, "colab-1")).thenReturn(false);

        assertThatThrownBy(() -> service.registrar(coordinador, "colab-1", TipoReconocimiento.RECONOCIMIENTO,
                null, "Buen trabajo", "Excelente cierre de mes", HOY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a tu cargo");

        verify(registroRepository, never()).save(any());
    }

    @Test
    void registrarExigeTituloYDescripcion() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_CREAR, "colab-1")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(coordinador, "colab-1", TipoReconocimiento.LLAMADO_ATENCION,
                null, "", "algo", HOY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("título");
    }

    @Test
    void registrarFallaConFechaFutura() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_CREAR, "colab-1")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(coordinador, "colab-1", TipoReconocimiento.RECONOCIMIENTO,
                null, "Título", "Descripción", HOY.plusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void registrarQuedaGuardadoCuandoTodoEsValido() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_CREAR, "colab-1")).thenReturn(true);

        RegistroReconocimiento registro = service.registrar(coordinador, "colab-1", TipoReconocimiento.RECONOCIMIENTO,
                "Desempeño", "Excelente trimestre", "Superó las metas del área", HOY, null);

        assertThat(registro.getColaborador()).isSameAs(colaborador);
        assertThat(registro.getRegistradoPor()).isSameAs(coordinador);
        assertThat(registro.getTipo()).isEqualTo(TipoReconocimiento.RECONOCIMIENTO);
        assertThat(registro.getArchivoSoporteClave()).isNull();
    }

    @Test
    void historialDeFallaSinPermisoSobreLaPersona() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_VER, "colab-1")).thenReturn(false);

        assertThatThrownBy(() -> service.historialDe(coordinador, "colab-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No puedes ver");
    }

    /**
     * Mismo bug que PerfilColaboradorService: un COORDINADOR solo tiene RECONOCIMIENTO_VER
     * en ámbito AREA, que nunca lo incluye a él mismo. Verse a uno mismo no debe depender
     * de ese chequeo.
     */
    @Test
    void historialDeSePermiteAUnoMismoAunqueElAmbitoSeaArea() {
        when(capacidadService.puedeSobre(Capacidades.RECONOCIMIENTO_VER, "coord-1")).thenReturn(false);
        when(registroRepository.historialDe("coord-1")).thenReturn(List.of());

        assertThat(service.historialDe(coordinador, "coord-1")).isEmpty();
    }
}
