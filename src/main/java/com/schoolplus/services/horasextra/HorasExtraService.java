package com.schoolplus.services.horasextra;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.RecargoTipo;
import com.schoolplus.model.RegistroHorasExtra;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RecargoTipoRepository;
import com.schoolplus.repository.RegistroHorasExtraRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Horas extra: el coordinador registra las de su equipo, Talento Humano las aprueba o
 * deniega (§4.6).
 *
 * <p>A diferencia de Solicitudes, esto no es una máquina de dos instancias: quien
 * registra nunca es quien decide, así que hay un solo paso de aprobación, no una cadena.
 * Igual que allí, esta clase no manda correo ni notifica dentro de la transacción —eso
 * es cosa de {@link NotificadorHorasExtra}, después del commit.</p>
 */
@Service
public class HorasExtraService {

    private static final Logger log = LoggerFactory.getLogger(HorasExtraService.class);
    private static final EnumSet<EstadoHorasExtra> ESTADOS_VIGENTES =
            EnumSet.of(EstadoHorasExtra.PENDIENTE, EstadoHorasExtra.APROBADO);

    private final RegistroHorasExtraRepository registroRepository;
    private final RecargoTipoRepository recargoTipoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadService capacidadService;

    public HorasExtraService(RegistroHorasExtraRepository registroRepository,
                             RecargoTipoRepository recargoTipoRepository,
                             UsuarioRepository usuarioRepository,
                             CapacidadService capacidadService) {
        this.registroRepository = registroRepository;
        this.recargoTipoRepository = recargoTipoRepository;
        this.usuarioRepository = usuarioRepository;
        this.capacidadService = capacidadService;
    }

    // ── Consultas de apoyo ───────────────────────────────────────────────────

    /** El equipo directo de un coordinador: a quién le puede registrar horas. */
    @Transactional(readOnly = true)
    public List<Usuario> equipoDe(Usuario coordinador) {
        return usuarioRepository.findByCoordinadorAndEstadoOrderByNombreAsc(coordinador, EstadoUsuario.ACTIVO);
    }

    @Transactional(readOnly = true)
    public List<RecargoTipo> tiposDeRecargo() {
        return recargoTipoRepository.findByActivoTrueOrderByNombreAsc();
    }

    // ── Registro ─────────────────────────────────────────────────────────────

    /**
     * Registra horas extra de un colaborador del equipo del coordinador.
     *
     * <p>{@code puedeSobre} exige ámbito AREA: el colaborador tiene que reportarle
     * directamente a quien registra, no a cualquiera (§4.6, misma regla que
     * {@code SOLICITUD_APROBAR_PRIMERA}).</p>
     */
    @Transactional
    public RegistroHorasExtra registrar(Usuario coordinador, String colaboradorId, String recargoCodigo,
                                        LocalDate fecha, BigDecimal horas, String observacion) {
        if (!capacidadService.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, colaboradorId)) {
            throw new IllegalArgumentException("Esa persona no está a tu cargo.");
        }
        if (fecha == null || fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede ser futura.");
        }
        if (horas == null || horas.signum() <= 0) {
            throw new IllegalArgumentException("Las horas tienen que ser un número mayor que cero.");
        }
        if (registroRepository.existsByColaboradorIdAndFechaAndRecargoCodigoAndEstadoIn(
                colaboradorId, fecha, recargoCodigo, ESTADOS_VIGENTES)) {
            throw new IllegalArgumentException(
                    "Ya hay un registro de ese tipo para esa persona ese día, pendiente o aprobado.");
        }

        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));
        RecargoTipo recargo = recargoTipoRepository.findById(recargoCodigo)
                .orElseThrow(() -> new IllegalArgumentException("Ese tipo de recargo no existe."));

        RegistroHorasExtra registro = new RegistroHorasExtra();
        registro.setId(UuidV7.nuevoId());
        registro.setColaborador(colaborador);
        registro.setRecargo(recargo);
        registro.setFecha(fecha);
        registro.setHoras(horas);
        registro.setObservacion(Textos.vacioANulo(observacion));
        registro.setRegistradoPor(coordinador);
        registro.setEstado(EstadoHorasExtra.PENDIENTE);

        registroRepository.save(registro);
        log.info("Horas extra registradas: {} horas de {} el {}, por {}",
                horas, colaborador.getEmail(), fecha, coordinador.getEmail());
        return registro;
    }

    // ── Listados ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RegistroHorasExtra> misRegistros(Usuario colaborador, Pageable pagina) {
        return registroRepository.misRegistros(colaborador.getId(), pagina);
    }

    @Transactional(readOnly = true)
    public Page<RegistroHorasExtra> registradosPor(Usuario coordinador, Pageable pagina) {
        return registroRepository.registradosPor(coordinador.getId(), pagina);
    }

    @Transactional(readOnly = true)
    public Page<RegistroHorasExtra> pendientes(Pageable pagina) {
        return registroRepository.pendientes(pagina);
    }

    /** Historial completo para Talento Humano: de cualquier coordinador, decidido o no. */
    @Transactional(readOnly = true)
    public Page<RegistroHorasExtra> todos(Pageable pagina) {
        return registroRepository.todos(pagina);
    }

    // ── Decisión de Talento Humano ───────────────────────────────────────────

    @Transactional
    public RegistroHorasExtra aprobar(Usuario supervisor, String registroId) {
        RegistroHorasExtra registro = cargarPendiente(registroId);
        registro.setEstado(EstadoHorasExtra.APROBADO);
        registro.setDecididoPor(supervisor);
        registro.setDecididoEn(LocalDateTime.now());
        registroRepository.save(registro);
        log.info("Horas extra {} aprobadas por {}", registroId, supervisor.getEmail());
        return registro;
    }

    /** Deniega. Motivo obligatorio, misma regla que Solicitudes. */
    @Transactional
    public RegistroHorasExtra rechazar(Usuario supervisor, String registroId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Denegar exige escribir un motivo.");
        }
        RegistroHorasExtra registro = cargarPendiente(registroId);
        registro.setEstado(EstadoHorasExtra.RECHAZADO);
        registro.setDecididoPor(supervisor);
        registro.setDecididoEn(LocalDateTime.now());
        registro.setMotivoRechazo(motivo);
        registroRepository.save(registro);
        log.info("Horas extra {} rechazadas por {}: {}", registroId, supervisor.getEmail(), motivo);
        return registro;
    }

    private RegistroHorasExtra cargarPendiente(String registroId) {
        RegistroHorasExtra registro = registroRepository.buscarCompleto(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Ese registro de horas extra no existe."));
        if (registro.getEstado() != EstadoHorasExtra.PENDIENTE) {
            throw new IllegalArgumentException("Ese registro ya fue decidido.");
        }
        return registro;
    }
}
