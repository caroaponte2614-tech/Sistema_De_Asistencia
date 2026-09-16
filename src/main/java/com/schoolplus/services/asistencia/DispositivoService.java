package com.schoolplus.services.asistencia;

import com.schoolplus.model.Ambito;
import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoDispositivo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AsistenciaDispositivoRepository;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * La vinculación de celulares, que es la mitad del antifraude (la otra mitad es que el QR
 * rote).
 *
 * <p>La regla: la primera vez que alguien registra asistencia, ese teléfono queda vinculado
 * a su cuenta. Desde otro teléfono no puede registrar hasta que un profesor suyo —o un
 * administrador— apruebe el cambio. Así, prestar el celular no sirve: el compañero
 * ausente no puede registrarse con él, y el presente tampoco puede registrar a dos.</p>
 *
 * <p>El teléfono se reconoce por una cookie aleatoria que pone el servidor. De ella solo se
 * guarda el SHA-256; el valor en claro nunca llega a la base.</p>
 */
@Service
public class DispositivoService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoService.class);

    private final AsistenciaDispositivoRepository dispositivoRepository;
    private final ClaseInscripcionRepository inscripcionRepository;
    private final CapacidadService capacidades;
    private final Clock reloj;

    public DispositivoService(AsistenciaDispositivoRepository dispositivoRepository,
                              ClaseInscripcionRepository inscripcionRepository,
                              CapacidadService capacidades,
                              Clock reloj) {
        this.dispositivoRepository = dispositivoRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.capacidades = capacidades;
        this.reloj = reloj;
    }

    /**
     * Devuelve el dispositivo con el que ese estudiante puede registrar ahora mismo, o
     * rechaza el intento.
     *
     * <p>Si el teléfono es nuevo y la persona todavía no tenía ninguno, queda vinculado en
     * el acto: pedir una aprobación para el primer registro de cada estudiante sería una
     * fila de cien personas el primer día.</p>
     */
    @Transactional
    public AsistenciaDispositivo autorizarParaRegistro(Usuario estudiante, String identificadorCrudo,
                                                       String userAgent) {
        if (identificadorCrudo == null || identificadorCrudo.isBlank()) {
            throw new RegistroRechazadoException(MotivoRechazo.DISPOSITIVO_NO_IDENTIFICADO);
        }
        String hash = hashDe(identificadorCrudo);

        Optional<AsistenciaDispositivo> activoPropio =
                dispositivoRepository.findByActivoEstudianteId(estudiante.getId());
        if (activoPropio.isPresent()) {
            if (activoPropio.get().getIdentificadorHash().equals(hash)) {
                return activoPropio.get();
            }
            // Cambió de teléfono: se deja pedida la autorización y se rechaza el registro.
            registrarSolicitudDeCambio(estudiante, hash, userAgent);
            throw new RegistroRechazadoException(MotivoRechazo.DISPOSITIVO_NO_AUTORIZADO);
        }

        if (dispositivoRepository.findByActivoIdentificador(hash).isPresent()) {
            throw new RegistroRechazadoException(MotivoRechazo.DISPOSITIVO_DE_OTRO_ESTUDIANTE);
        }

        AsistenciaDispositivo nuevo = nuevo(estudiante, hash, userAgent);
        nuevo.activar(LocalDateTime.now(reloj));
        log.info("Celular vinculado a {} por primera vez", estudiante.getEmail());
        return dispositivoRepository.save(nuevo);
    }

    /** Las solicitudes de cambio que ese docente (o admin) puede resolver. */
    @Transactional(readOnly = true)
    public List<AsistenciaDispositivo> pendientesPara(Usuario actor) {
        Optional<Ambito> ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR);
        if (ambito.isEmpty()) {
            return List.of();
        }
        // Con alcance TODOS se ven todas; con PROPIO, solo las de estudiantes de sus clases.
        String profesorId = ambito.get() == Ambito.TODOS ? null : actor.getId();
        return dispositivoRepository.pendientes(profesorId);
    }

    @Transactional
    public AsistenciaDispositivo aprobarCambio(Usuario actor, String dispositivoId) {
        AsistenciaDispositivo solicitud = cargar(dispositivoId);
        exigirPuedeSobre(actor, solicitud.getEstudiante());
        if (solicitud.getEstado() != EstadoDispositivo.PENDIENTE) {
            throw new IllegalArgumentException("Esa solicitud ya fue resuelta.");
        }
        Optional<AsistenciaDispositivo> deOtro =
                dispositivoRepository.findByActivoIdentificador(solicitud.getIdentificadorHash());
        if (deOtro.isPresent() && !deOtro.get().getEstudiante().getId().equals(solicitud.getEstudiante().getId())) {
            throw new IllegalArgumentException("Ese celular está vinculado a otro estudiante.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        // El anterior tiene que soltar los candados ANTES de que el nuevo los tome: son
        // columnas únicas, y hacerlo al revés choca contra la restricción.
        dispositivoRepository.findByActivoEstudianteId(solicitud.getEstudiante().getId())
                .ifPresent(anterior -> {
                    anterior.desactivar(EstadoDispositivo.REEMPLAZADO, actor, ahora);
                    dispositivoRepository.saveAndFlush(anterior);
                });

        solicitud.activar(ahora);
        solicitud.setResueltoPor(actor);
        solicitud.setResueltoEn(ahora);
        log.info("Cambio de celular aprobado para {} por {}",
                solicitud.getEstudiante().getEmail(), actor.getEmail());
        return dispositivoRepository.save(solicitud);
    }

    @Transactional
    public AsistenciaDispositivo rechazarCambio(Usuario actor, String dispositivoId) {
        AsistenciaDispositivo solicitud = cargar(dispositivoId);
        exigirPuedeSobre(actor, solicitud.getEstudiante());
        if (solicitud.getEstado() != EstadoDispositivo.PENDIENTE) {
            throw new IllegalArgumentException("Esa solicitud ya fue resuelta.");
        }
        solicitud.desactivar(EstadoDispositivo.RECHAZADO, actor, LocalDateTime.now(reloj));
        return dispositivoRepository.save(solicitud);
    }

    /**
     * Desvincula el celular activo de un estudiante: el siguiente que use queda vinculado
     * solo, sin aprobación. Es la salida para "perdí el teléfono" sin papeleo.
     */
    @Transactional
    public void desvincular(Usuario actor, String dispositivoId) {
        AsistenciaDispositivo dispositivo = cargar(dispositivoId);
        exigirPuedeSobre(actor, dispositivo.getEstudiante());
        dispositivo.desactivar(EstadoDispositivo.REVOCADO, actor, LocalDateTime.now(reloj));
        dispositivoRepository.save(dispositivo);
        log.info("Celular de {} desvinculado por {}", dispositivo.getEstudiante().getEmail(), actor.getEmail());
    }

    @Transactional(readOnly = true)
    public List<AsistenciaDispositivo> historialDe(String estudianteId) {
        return dispositivoRepository.findByEstudianteIdOrderBySolicitadoEnDesc(estudianteId);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void registrarSolicitudDeCambio(Usuario estudiante, String hash, String userAgent) {
        boolean yaPedido = dispositivoRepository
                .findFirstByEstudianteIdAndIdentificadorHashAndEstado(
                        estudiante.getId(), hash, EstadoDispositivo.PENDIENTE)
                .isPresent();
        if (yaPedido) {
            return;
        }
        dispositivoRepository.save(nuevo(estudiante, hash, userAgent));
        log.info("Solicitud de cambio de celular registrada para {}", estudiante.getEmail());
    }

    private AsistenciaDispositivo nuevo(Usuario estudiante, String hash, String userAgent) {
        AsistenciaDispositivo dispositivo = new AsistenciaDispositivo();
        dispositivo.setId(UuidV7.nuevoId());
        dispositivo.setEstudiante(estudiante);
        dispositivo.setIdentificadorHash(hash);
        dispositivo.setDescripcion(describir(userAgent));
        dispositivo.setEstado(EstadoDispositivo.PENDIENTE);
        return dispositivo;
    }

    private AsistenciaDispositivo cargar(String dispositivoId) {
        return dispositivoRepository.buscarConEstudiante(dispositivoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese dispositivo no existe."));
    }

    /**
     * Quién puede autorizar: con alcance TODOS, cualquiera; con PROPIO, solo sobre un
     * estudiante que esté en alguna de sus clases.
     */
    private void exigirPuedeSobre(Usuario actor, Usuario estudiante) {
        Ambito ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)
                .orElseThrow(() -> new IllegalArgumentException("No puedes autorizar celulares."));
        if (ambito == Ambito.TODOS) {
            return;
        }
        if (!inscripcionRepository.existsByEstudianteIdAndClaseProfesorIdAndActivaTrue(
                estudiante.getId(), actor.getId())) {
            throw new IllegalArgumentException("Ese estudiante no está en ninguna de tus clases.");
        }
    }

    /** "Android · Chrome" a partir del user agent, para que el profesor sepa qué aprueba. */
    static String describir(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Dispositivo desconocido";
        }
        String sistema = userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iPhone/iPad"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS") ? "Mac"
                : "Otro";
        String navegador = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("OPR/") ? "Opera"
                : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Safari/") ? "Safari"
                : "Navegador";
        return sistema + " · " + navegador;
    }

    static String hashDe(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
