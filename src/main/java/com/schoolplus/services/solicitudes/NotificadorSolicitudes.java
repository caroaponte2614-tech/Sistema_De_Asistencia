package com.schoolplus.services.solicitudes;

import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.model.Solicitud;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.email.EmailService;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Avisa por correo, y ahora también por la campanita, a quien le toca mover ficha
 * (§4.4).
 *
 * <p>Se llama <strong>después</strong> de que la transacción del cambio de estado haya
 * confirmado, nunca dentro: el §5.2 prohíbe transacciones abiertas durante llamadas de
 * red, y una conexión SMTP lenta bloquearía la fila de la base de datos todo ese rato.
 * La notificación en sí es una escritura rápida (no lleva red), así que no hace falta
 * que sea async — pero como vive en el mismo método {@code @Async} que el correo,
 * viaja en el mismo hilo de fondo sin costo extra.</p>
 *
 * <p>Es {@code @Async} por lo mismo: quien aprueba no tiene que esperar a que Gmail
 * conteste. Y si el correo falla, el trámite ya está guardado — se queja en el log y
 * sigue. Un aviso perdido es molesto; una aprobación perdida es un problema.</p>
 */
@Service
public class NotificadorSolicitudes {

    private static final Logger log = LoggerFactory.getLogger(NotificadorSolicitudes.class);
    private static final String RUTA_MIS_SOLICITUDES = "/mi-espacio/solicitudes";
    private static final String RUTA_BANDEJA_COORDINACION = "/coordinacion/bandeja";

    private final EmailService emailService;
    private final NotificacionService notificacionService;

    public NotificadorSolicitudes(EmailService emailService, NotificacionService notificacionService) {
        this.emailService = emailService;
        this.notificacionService = notificacionService;
    }

    /** Le llegó algo por resolver a un aprobador. */
    @Async
    public void avisarPendiente(Solicitud solicitud, Usuario destinatario) {
        if (destinatario == null) {
            return;
        }
        String cuerpo = solicitud.getSolicitante().getNombre() + " pidió "
                + solicitud.getTipo().getNombre().toLowerCase()
                + " del " + solicitud.getFechaInicio() + " al " + solicitud.getFechaFin()
                + " (" + solicitud.getDiasHabiles().stripTrailingZeros().toPlainString()
                + " días hábiles). Entra a School_Plus para aprobarla o denegarla.";

        enviar(destinatario, "Tienes una solicitud por resolver — School_Plus",
                "Pendiente de tu decisión", cuerpo);
        notificacionService.crear(destinatario.getId(), "SOLICITUD_PENDIENTE",
                "Tienes una solicitud por resolver", cuerpo, RUTA_BANDEJA_COORDINACION);
    }

    /** Al solicitante: alguien decidió sobre lo suyo. */
    @Async
    public void avisarDecision(Solicitud solicitud, EstadoSolicitud resultado, String comentario) {
        Usuario solicitante = solicitud.getSolicitante();
        String titulo = switch (resultado) {
            case APROBADO -> "Tu solicitud quedó aprobada";
            case PENDIENTE_SUPERVISOR -> "Tu solicitud pasó a Talento Humano";
            case RECHAZADO_COORD -> "Tu coordinador denegó tu solicitud";
            case RECHAZADO_RRHH -> "Talento Humano denegó tu solicitud";
            default -> "Tu solicitud cambió de estado";
        };

        String cuerpo = solicitud.getTipo().getNombre()
                + " del " + solicitud.getFechaInicio() + " al " + solicitud.getFechaFin() + ". "
                + (comentario == null || comentario.isBlank() ? "" : "Comentario: " + comentario);

        enviar(solicitante, titulo + " — School_Plus", "Tu solicitud", cuerpo);
        notificacionService.crear(solicitante.getId(), "SOLICITUD_DECISION", titulo, cuerpo,
                RUTA_MIS_SOLICITUDES);
    }

    private void enviar(Usuario destinatario, String asunto, String antetitulo, String cuerpo) {
        try {
            emailService.enviarAvisoSolicitud(
                    destinatario.getEmail(), destinatario.getNombre(), asunto, antetitulo, cuerpo);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar a {}: {}", destinatario.getEmail(), e.getMessage(), e);
        }
    }
}
