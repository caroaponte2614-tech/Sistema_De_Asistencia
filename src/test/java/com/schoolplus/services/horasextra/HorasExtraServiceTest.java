package com.schoolplus.services.horasextra;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.RecargoTipo;
import com.schoolplus.model.RegistroHorasExtra;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RecargoTipoRepository;
import com.schoolplus.repository.RegistroHorasExtraRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §4.6: el coordinador registra, Talento Humano aprueba o deniega — una sola instancia,
 * a diferencia de Solicitudes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HorasExtraServiceTest {

    // Dinámico y no fijo a propósito: el servicio valida contra LocalDate.now() real, así
    // que una fecha fija (antes 2026-09-08) deja de ser "hoy" en cuanto el reloj la alcanza
    // y "mañana" deja de ser futuro.
    private static final LocalDate HOY = LocalDate.now();

    @Mock
    private RegistroHorasExtraRepository registroRepository;
    @Mock
    private RecargoTipoRepository recargoTipoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CapacidadService capacidadService;

    private HorasExtraService service;

    private Usuario coordinador;
    private Usuario colaborador;
    private Usuario supervisor;
    private RecargoTipo diurna;

    @BeforeEach
    void preparar() {
        service = new HorasExtraService(registroRepository, recargoTipoRepository, usuarioRepository, capacidadService);

        coordinador = usuario("coord-1", "coordinador@schoolplus.example", Rol.COORDINADOR);
        supervisor = usuario("sup-1", "rrhh@schoolplus.example", Rol.SUPERVISOR);
        colaborador = usuario("colab-1", "colaborador@schoolplus.example", Rol.USUARIO);
        colaborador.setCoordinador(coordinador);

        diurna = new RecargoTipo();
        diurna.setCodigo("DIURNA");
        diurna.setNombre("Hora extra diurna");

        lenient().when(usuarioRepository.findById(colaborador.getId())).thenReturn(Optional.of(colaborador));
        lenient().when(recargoTipoRepository.findById("DIURNA")).thenReturn(Optional.of(diurna));
        lenient().when(registroRepository.save(any(RegistroHorasExtra.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Registrar ────────────────────────────────────────────────────────────

    @Test
    void registrarQuedaEnPendienteCuandoTodoEsValido() {
        when(capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaborador.getId())).thenReturn(true);

        RegistroHorasExtra registro = service.registrar(
                coordinador, colaborador.getId(), "DIURNA", HOY, new BigDecimal("2.5"), "Cierre de mes");

        assertThat(registro.getEstado()).isEqualTo(EstadoHorasExtra.PENDIENTE);
        assertThat(registro.getColaborador()).isSameAs(colaborador);
        assertThat(registro.getRegistradoPor()).isSameAs(coordinador);
        assertThat(registro.getRecargo()).isSameAs(diurna);
        assertThat(registro.getHoras()).isEqualByComparingTo("2.5");
    }

    @Test
    void noSePuedeRegistrarSobreQuienNoEstaACargo() {
        when(capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaborador.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.registrar(
                coordinador, colaborador.getId(), "DIURNA", HOY, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a tu cargo");

        verify(registroRepository, never()).save(any());
    }

    @Test
    void noSePuedeRegistrarUnaFechaFutura() {
        when(capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaborador.getId())).thenReturn(true);
        LocalDate manana = HOY.plusDays(1);

        assertThatThrownBy(() -> service.registrar(
                coordinador, colaborador.getId(), "DIURNA", manana, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void lasHorasTienenQueSerPositivas() {
        when(capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaborador.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(
                coordinador, colaborador.getId(), "DIURNA", HOY, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    void noSeDuplicaUnRegistroVigenteDelMismoDiaYTipo() {
        when(capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaborador.getId())).thenReturn(true);
        when(registroRepository.existsByColaboradorIdAndFechaAndRecargoCodigoAndEstadoIn(
                eq(colaborador.getId()), eq(HOY), eq("DIURNA"), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(
                coordinador, colaborador.getId(), "DIURNA", HOY, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya hay un registro");
    }

    // ── Aprobar / rechazar ───────────────────────────────────────────────────

    @Test
    void aprobarMuevaAAprobadoYDejaTrazabilidad() {
        RegistroHorasExtra pendiente = registroPendiente();
        when(registroRepository.buscarCompleto("reg-1")).thenReturn(Optional.of(pendiente));

        RegistroHorasExtra decidido = service.aprobar(supervisor, "reg-1");

        assertThat(decidido.getEstado()).isEqualTo(EstadoHorasExtra.APROBADO);
        assertThat(decidido.getDecididoPor()).isSameAs(supervisor);
        assertThat(decidido.getDecididoEn()).isNotNull();
    }

    @Test
    void rechazarExigeMotivo() {
        RegistroHorasExtra pendiente = registroPendiente();
        when(registroRepository.buscarCompleto("reg-1")).thenReturn(Optional.of(pendiente));

        assertThatThrownBy(() -> service.rechazar(supervisor, "reg-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        verify(registroRepository, never()).save(any());
    }

    @Test
    void rechazarConMotivoMuevaARechazado() {
        RegistroHorasExtra pendiente = registroPendiente();
        when(registroRepository.buscarCompleto("reg-1")).thenReturn(Optional.of(pendiente));

        RegistroHorasExtra decidido = service.rechazar(supervisor, "reg-1", "No coincide con el biométrico");

        assertThat(decidido.getEstado()).isEqualTo(EstadoHorasExtra.RECHAZADO);
        assertThat(decidido.getMotivoRechazo()).isEqualTo("No coincide con el biométrico");
    }

    @Test
    void noSePuedeDecidirDosVeces() {
        RegistroHorasExtra yaDecidido = registroPendiente();
        yaDecidido.setEstado(EstadoHorasExtra.APROBADO);
        when(registroRepository.buscarCompleto("reg-1")).thenReturn(Optional.of(yaDecidido));

        assertThatThrownBy(() -> service.aprobar(supervisor, "reg-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya fue decidido");
    }

    // ── Equipo ───────────────────────────────────────────────────────────────

    @Test
    void equipoDeDelegaEnElRepositorio() {
        when(usuarioRepository.findByCoordinadorAndEstadoOrderByNombreAsc(
                eq(coordinador), any())).thenReturn(java.util.List.of(colaborador));

        assertThat(service.equipoDe(coordinador)).containsExactly(colaborador);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private RegistroHorasExtra registroPendiente() {
        RegistroHorasExtra registro = new RegistroHorasExtra();
        registro.setId("reg-1");
        registro.setColaborador(colaborador);
        registro.setRecargo(diurna);
        registro.setFecha(HOY);
        registro.setHoras(new BigDecimal("2"));
        registro.setRegistradoPor(coordinador);
        registro.setEstado(EstadoHorasExtra.PENDIENTE);
        return registro;
    }

    private Usuario usuario(String id, String email, String rolCodigo) {
        Rol rol = new Rol();
        rol.setCodigo(rolCodigo);
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setNombre(email);
        usuario.setRoles(Set.of(rol));
        return usuario;
    }
}
