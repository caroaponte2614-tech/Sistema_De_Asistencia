package com.schoolplus.services.vacaciones;

import com.schoolplus.model.Solicitud;
import com.schoolplus.model.TipoMovimientoVacaciones;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.VacacionesMovimiento;
import com.schoolplus.model.VacacionesSaldo;
import com.schoolplus.repository.VacacionesMovimientoRepository;
import com.schoolplus.repository.VacacionesSaldoRepository;
import com.schoolplus.services.solicitudes.SaldoInsuficienteException;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Saldo de vacaciones: causación, reserva, consumo y reversa (§4.5).
 *
 * <h2>Causación</h2>
 * <strong>1,25 días por mes trabajado</strong>, confirmado por el usuario el 2026-08-26.
 * Equivale a los 15 días hábiles por año de la ley colombiana repartidos mes a mes. El
 * valor es configurable ({@code app.vacaciones.dias-por-mes}) porque una cifra legal no
 * debería estar enterrada en el código.
 *
 * <h2>Por qué hay reserva y no solo consumo</h2>
 * Entre que alguien pide vacaciones y que Talento Humano las valida pasan días. En ese
 * hueco los días no están tomados, pero tampoco disponibles. Sin la reserva, quien pide
 * dos veces seguidas gastaría el mismo saldo dos veces.
 */
@Service
public class VacacionesService {

    private static final Logger log = LoggerFactory.getLogger(VacacionesService.class);
    private static final int ESCALA = 2;

    private final VacacionesSaldoRepository saldoRepository;
    private final VacacionesMovimientoRepository movimientoRepository;
    private final BigDecimal diasPorMes;

    public VacacionesService(VacacionesSaldoRepository saldoRepository,
                             VacacionesMovimientoRepository movimientoRepository,
                             @Value("${app.vacaciones.dias-por-mes:1.25}") BigDecimal diasPorMes) {
        this.saldoRepository = saldoRepository;
        this.movimientoRepository = movimientoRepository;
        this.diasPorMes = diasPorMes;
    }

    public BigDecimal getDiasPorMes() {
        return diasPorMes;
    }

    // ── Consulta ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VacacionesSaldo> saldosDe(String usuarioId) {
        return saldoRepository.saldosDe(usuarioId);
    }

    /** Días disponibles hoy, sumando todos los periodos. */
    @Transactional(readOnly = true)
    public BigDecimal disponibleTotal(String usuarioId) {
        return saldoRepository.saldosDe(usuarioId).stream()
                .map(VacacionesSaldo::disponible)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Causación ────────────────────────────────────────────────────────────

    /**
     * Pone al día los días causados de una persona hasta hoy.
     *
     * <p>Es idempotente: calcula cuántos días le corresponden por los meses completos
     * trabajados y ajusta la diferencia. Llamarlo dos veces el mismo día no duplica nada,
     * que es lo que hace seguro engancharlo a una tarea programada.</p>
     *
     * <p>Sin {@code fecha_ingreso} no se causa nada: no hay desde cuándo contar.</p>
     */
    @Transactional
    public VacacionesSaldo causarHasta(Usuario usuario, LocalDate hasta) {
        short periodo = (short) hasta.getYear();
        VacacionesSaldo saldo = saldoDe(usuario, periodo);

        if (usuario.getFechaIngreso() == null) {
            log.debug("{} no tiene fecha de ingreso: no se causa nada.", usuario.getEmail());
            return saldo;
        }

        LocalDate desde = usuario.getFechaIngreso().isAfter(LocalDate.of(periodo, 1, 1))
                ? usuario.getFechaIngreso()
                : LocalDate.of(periodo, 1, 1);

        if (desde.isAfter(hasta)) {
            return saldo;
        }

        long mesesCompletos = mesesCalendarioCompletos(desde, hasta);
        BigDecimal correspondientes = diasPorMes
                .multiply(BigDecimal.valueOf(mesesCompletos))
                .setScale(ESCALA, java.math.RoundingMode.HALF_UP);

        BigDecimal diferencia = correspondientes.subtract(saldo.getDiasCausados());
        if (diferencia.signum() == 0) {
            return saldo;
        }

        saldo.setDiasCausados(correspondientes);
        saldoRepository.save(saldo);
        anotar(saldo, TipoMovimientoVacaciones.CAUSACION, diferencia, null, null,
                mesesCompletos + " meses desde " + desde + " a " + diasPorMes + " días/mes");

        log.info("Causados {} días a {} (periodo {}): total {}",
                diferencia, usuario.getEmail(), periodo, correspondientes);
        return saldo;
    }

    /**
     * Meses de calendario trabajados <em>enteros</em> entre las dos fechas.
     *
     * <p>No sirve {@code ChronoUnit.MONTHS.between}: del 1 de enero al 31 de diciembre
     * devuelve <strong>11</strong>, porque le falta un día para el duodécimo mes completo.
     * Con el periodo llevado por año calendario, eso hacía que nadie llegara nunca a los
     * 15 días y se perdieran 1,25 días por persona y año.</p>
     *
     * <p>Lo que se cuenta aquí es otra cosa: cuántos meses del calendario transcurrieron
     * completos estando la persona vinculada. Enero-diciembre son 12; entrar el 15 de
     * marzo y mirar el 30 de junio son 3 (abril, mayo y junio), porque marzo no se
     * trabajó entero.</p>
     */
    static long mesesCalendarioCompletos(LocalDate desde, LocalDate hasta) {
        // Primer mes que se trabaja completo.
        LocalDate primerMes = desde.getDayOfMonth() == 1
                ? desde.withDayOfMonth(1)
                : desde.withDayOfMonth(1).plusMonths(1);

        // Último mes que ya terminó a la fecha de corte.
        boolean elMesDeCorteTermino = hasta.getDayOfMonth() == hasta.lengthOfMonth();
        LocalDate ultimoMes = elMesDeCorteTermino
                ? hasta.withDayOfMonth(1)
                : hasta.withDayOfMonth(1).minusMonths(1);

        long meses = ChronoUnit.MONTHS.between(primerMes, ultimoMes) + 1;
        return Math.max(0, meses);
    }

    // ── Movimientos ligados al flujo de aprobación ───────────────────────────

    /**
     * Aparta los días al enviar la solicitud.
     *
     * <p>Toma el saldo con bloqueo pesimista: comprobar y reservar tiene que ser atómico
     * o dos solicitudes simultáneas pasan las dos (§5.2).</p>
     */
    @Transactional
    public void reservar(Solicitud solicitud) {
        Usuario usuario = solicitud.getSolicitante();
        short periodo = (short) solicitud.getFechaInicio().getYear();
        BigDecimal dias = solicitud.getDiasHabiles();

        VacacionesSaldo saldo = saldoRepository.bloquearParaActualizar(usuario.getId(), periodo)
                .orElseGet(() -> saldoDe(usuario, periodo));

        BigDecimal disponible = saldo.disponible();
        if (disponible.compareTo(dias) < 0) {
            throw new SaldoInsuficienteException(dias, disponible);
        }

        saldo.setDiasEnTramite(saldo.getDiasEnTramite().add(dias));
        saldoRepository.save(saldo);
        anotar(saldo, TipoMovimientoVacaciones.RESERVA, dias, solicitud, null,
                "Solicitud enviada a aprobación");
    }

    /** La reserva se vuelve consumo: la solicitud quedó aprobada. */
    @Transactional
    public void consumir(Solicitud solicitud, Usuario actor) {
        VacacionesSaldo saldo = saldoBloqueado(solicitud);
        BigDecimal dias = solicitud.getDiasHabiles();

        saldo.setDiasEnTramite(saldo.getDiasEnTramite().subtract(dias).max(BigDecimal.ZERO));
        saldo.setDiasTomados(saldo.getDiasTomados().add(dias));
        saldoRepository.save(saldo);
        anotar(saldo, TipoMovimientoVacaciones.CONSUMO, dias, solicitud, actor,
                "Solicitud aprobada");
    }

    /** La reserva se devuelve: denegaron en alguna de las dos instancias. */
    @Transactional
    public void revertirReserva(Solicitud solicitud, Usuario actor, String motivo) {
        VacacionesSaldo saldo = saldoBloqueado(solicitud);
        BigDecimal dias = solicitud.getDiasHabiles();

        saldo.setDiasEnTramite(saldo.getDiasEnTramite().subtract(dias).max(BigDecimal.ZERO));
        saldoRepository.save(saldo);
        anotar(saldo, TipoMovimientoVacaciones.REVERSA, dias, solicitud, actor, motivo);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private VacacionesSaldo saldoBloqueado(Solicitud solicitud) {
        short periodo = (short) solicitud.getFechaInicio().getYear();
        return saldoRepository
                .bloquearParaActualizar(solicitud.getSolicitante().getId(), periodo)
                .orElseGet(() -> saldoDe(solicitud.getSolicitante(), periodo));
    }

    private VacacionesSaldo saldoDe(Usuario usuario, short periodo) {
        return saldoRepository.findByUsuarioIdAndPeriodo(usuario.getId(), periodo)
                .orElseGet(() -> {
                    VacacionesSaldo nuevo = new VacacionesSaldo();
                    nuevo.setId(UuidV7.nuevoId());
                    nuevo.setUsuario(usuario);
                    nuevo.setPeriodo(periodo);
                    return saldoRepository.save(nuevo);
                });
    }

    private void anotar(VacacionesSaldo saldo, TipoMovimientoVacaciones tipo, BigDecimal dias,
                        Solicitud solicitud, Usuario actor, String motivo) {
        VacacionesMovimiento movimiento = new VacacionesMovimiento();
        movimiento.setSaldo(saldo);
        movimiento.setTipo(tipo);
        movimiento.setDias(dias);
        movimiento.setSolicitud(solicitud);
        movimiento.setRegistradoPor(actor);
        movimiento.setMotivo(motivo);
        movimientoRepository.save(movimiento);
    }
}
