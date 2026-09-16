package com.schoolplus.services.vacaciones;

import com.schoolplus.model.Solicitud;
import com.schoolplus.model.TipoMovimientoVacaciones;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.VacacionesMovimiento;
import com.schoolplus.model.VacacionesSaldo;
import com.schoolplus.repository.VacacionesMovimientoRepository;
import com.schoolplus.repository.VacacionesSaldoRepository;
import com.schoolplus.services.solicitudes.SaldoInsuficienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Saldo de vacaciones: causación a 1,25 días/mes (confirmado por el usuario el
 * 2026-08-26) y el ciclo reserva → consumo / reversa del §4.5.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VacacionesServiceTest {

    private static final BigDecimal DIAS_POR_MES = new BigDecimal("1.25");

    @Mock
    private VacacionesSaldoRepository saldoRepository;
    @Mock
    private VacacionesMovimientoRepository movimientoRepository;

    private VacacionesService vacaciones;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        vacaciones = new VacacionesService(saldoRepository, movimientoRepository, DIAS_POR_MES);

        usuario = new Usuario();
        usuario.setId("colab-1");
        usuario.setEmail("colaborador@schoolplus.example");

        when(saldoRepository.save(any(VacacionesSaldo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void doceMesesTrabajadosCausanLosQuinceDiasDeLey() {
        usuario.setFechaIngreso(LocalDate.of(2025, 1, 1));
        saldoExistente(BigDecimal.ZERO);

        VacacionesSaldo saldo = vacaciones.causarHasta(usuario, LocalDate.of(2026, 12, 31));

        // 1,25 x 12 = 15 días hábiles al año.
        assertThat(saldo.getDiasCausados()).isEqualByComparingTo("15.00");
    }

    @Test
    void elUltimoMesDelAnoTambienCuenta() {
        // Este caso destapó un error real: ChronoUnit.MONTHS.between(1-ene, 31-dic) es 11,
        // no 12, así que se perdían 1,25 días por persona y año.
        assertThat(VacacionesService.mesesCalendarioCompletos(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isEqualTo(12);
    }

    @Test
    void elMesDeIngresoNoCuentaSiNoSeTrabajoEntero() {
        // Entra el 15 de marzo: abril, mayo y junio son los tres meses completos.
        assertThat(VacacionesService.mesesCalendarioCompletos(
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 6, 30))).isEqualTo(3);
    }

    @Test
    void unMesEmpezadoNoCausaNada() {
        assertThat(VacacionesService.mesesCalendarioCompletos(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 15))).isZero();
    }

    @Test
    void causaProporcionalALosMesesCumplidos() {
        usuario.setFechaIngreso(LocalDate.of(2026, 1, 1));
        saldoExistente(BigDecimal.ZERO);

        VacacionesSaldo saldo = vacaciones.causarHasta(usuario, LocalDate.of(2026, 5, 1));

        // Cuatro meses cumplidos: 1,25 x 4 = 5.
        assertThat(saldo.getDiasCausados()).isEqualByComparingTo("5.00");
    }

    @Test
    void causarDosVecesElMismoDiaNoDuplicaNada() {
        usuario.setFechaIngreso(LocalDate.of(2026, 1, 1));
        VacacionesSaldo saldo = saldoExistente(BigDecimal.ZERO);
        LocalDate hoy = LocalDate.of(2026, 7, 1);

        vacaciones.causarHasta(usuario, hoy);
        BigDecimal trasLaPrimera = saldo.getDiasCausados();
        vacaciones.causarHasta(usuario, hoy);

        assertThat(saldo.getDiasCausados()).isEqualByComparingTo(trasLaPrimera);
    }

    @Test
    void sinFechaDeIngresoNoSeCausaNada() {
        usuario.setFechaIngreso(null);
        saldoExistente(BigDecimal.ZERO);

        VacacionesSaldo saldo = vacaciones.causarHasta(usuario, LocalDate.of(2026, 12, 31));

        assertThat(saldo.getDiasCausados()).isEqualByComparingTo("0");
    }

    @Test
    void elDisponibleDescuentaLoTomadoYLoQueEstaEnTramite() {
        VacacionesSaldo saldo = new VacacionesSaldo();
        saldo.setDiasCausados(new BigDecimal("15"));
        saldo.setDiasTomados(new BigDecimal("5"));
        saldo.setDiasEnTramite(new BigDecimal("3"));

        assertThat(saldo.disponible()).isEqualByComparingTo("7");
    }

    @Test
    void noSePuedePedirMasDeLoDisponible() {
        saldoBloqueado(new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO);
        Solicitud solicitud = solicitudDe("5");

        assertThatThrownBy(() -> vacaciones.reservar(solicitud))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("2");
    }

    @Test
    void loQueEstaEnTramiteYaNoSePuedeVolverAPedir() {
        // 15 causados, 12 ya reservados por otra solicitud: quedan 3.
        saldoBloqueado(new BigDecimal("15"), BigDecimal.ZERO, new BigDecimal("12"));

        assertThatThrownBy(() -> vacaciones.reservar(solicitudDe("5")))
                .isInstanceOf(SaldoInsuficienteException.class);
    }

    @Test
    void reservarApartaLosDiasYDejaMovimiento() {
        VacacionesSaldo saldo = saldoBloqueado(new BigDecimal("15"), BigDecimal.ZERO, BigDecimal.ZERO);

        vacaciones.reservar(solicitudDe("5"));

        assertThat(saldo.getDiasEnTramite()).isEqualByComparingTo("5");
        assertThat(saldo.getDiasTomados()).isEqualByComparingTo("0");
        assertThat(ultimoMovimiento().getTipo()).isEqualTo(TipoMovimientoVacaciones.RESERVA);
    }

    @Test
    void aprobarConvierteLaReservaEnDiasTomados() {
        VacacionesSaldo saldo = saldoBloqueado(new BigDecimal("15"), BigDecimal.ZERO, new BigDecimal("5"));

        vacaciones.consumir(solicitudDe("5"), usuario);

        assertThat(saldo.getDiasEnTramite()).isEqualByComparingTo("0");
        assertThat(saldo.getDiasTomados()).isEqualByComparingTo("5");
        assertThat(saldo.disponible()).isEqualByComparingTo("10");
    }

    @Test
    void denegarDevuelveLosDiasSinTocarLosTomados() {
        VacacionesSaldo saldo = saldoBloqueado(new BigDecimal("15"), BigDecimal.ZERO, new BigDecimal("5"));

        vacaciones.revertirReserva(solicitudDe("5"), usuario, "Denegada por el coordinador");

        assertThat(saldo.getDiasEnTramite()).isEqualByComparingTo("0");
        assertThat(saldo.getDiasTomados()).isEqualByComparingTo("0");
        assertThat(saldo.disponible()).isEqualByComparingTo("15");
        assertThat(ultimoMovimiento().getTipo()).isEqualTo(TipoMovimientoVacaciones.REVERSA);
    }

    // ── Ayudas ───────────────────────────────────────────────────────────────

    private VacacionesSaldo saldoExistente(BigDecimal causados) {
        VacacionesSaldo saldo = nuevoSaldo(causados, BigDecimal.ZERO, BigDecimal.ZERO);
        when(saldoRepository.findByUsuarioIdAndPeriodo(anyString(), anyShort()))
                .thenReturn(Optional.of(saldo));
        return saldo;
    }

    private VacacionesSaldo saldoBloqueado(BigDecimal causados, BigDecimal tomados, BigDecimal enTramite) {
        VacacionesSaldo saldo = nuevoSaldo(causados, tomados, enTramite);
        when(saldoRepository.bloquearParaActualizar(anyString(), anyShort()))
                .thenReturn(Optional.of(saldo));
        return saldo;
    }

    private VacacionesSaldo nuevoSaldo(BigDecimal causados, BigDecimal tomados, BigDecimal enTramite) {
        VacacionesSaldo saldo = new VacacionesSaldo();
        saldo.setId("saldo-1");
        saldo.setUsuario(usuario);
        saldo.setPeriodo((short) 2026);
        saldo.setDiasCausados(causados);
        saldo.setDiasTomados(tomados);
        saldo.setDiasEnTramite(enTramite);
        return saldo;
    }

    private Solicitud solicitudDe(String dias) {
        Solicitud solicitud = new Solicitud();
        solicitud.setId("sol-1");
        solicitud.setSolicitante(usuario);
        solicitud.setFechaInicio(LocalDate.of(2026, 9, 7));
        solicitud.setFechaFin(LocalDate.of(2026, 9, 11));
        solicitud.setDiasHabiles(new BigDecimal(dias));
        return solicitud;
    }

    private VacacionesMovimiento ultimoMovimiento() {
        ArgumentCaptor<VacacionesMovimiento> captor =
                ArgumentCaptor.forClass(VacacionesMovimiento.class);
        org.mockito.Mockito.verify(movimientoRepository, org.mockito.Mockito.atLeastOnce())
                .save(captor.capture());
        return captor.getValue();
    }
}
