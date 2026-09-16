package com.schoolplus.services.horasextra;

import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.RegistroHorasExtra;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.email.EmailService;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Avisa por correo y por la campanita cuando hay horas extra por resolver, y cuando ya
 * se resolvieron. Mismo motivo para ser {@code @Async} que {@code NotificadorSolicitudes}:
 * corre después de que la transacción del registro ya confirmó, así que un correo lento
 * no bloquea al coordinador ni al supervisor.
 */
@Service
public class NotificadorHorasExtra {

    private static final Logger log = LoggerFactory.getLogger(NotificadorHorasExtra.class);
    private static final String RUTA_BANDEJA_SUPERVISOR = "/talento-humano/horas-extras";
    private static final String RUTA_MIS_REGISTROS = "/coordinacion/horas-extras";

    private final EmailService emailService;
    private final NotificacionService notificacionService;

    public NotificadorHorasExtra(EmailService emailService, NotificacionService notificacionService) {
        this.emailService = emailService;
        this.notificacionService = notificacionService;
    }

    /** Un coordinador registró horas extra: le llega a todos los supervisores. */
    @Async
    public void avisarPendiente(RegistroHorasExtra registro, List<Usuario> supervisores) {
        String cuerpo = registro.getRegistradoPor().getNombre() + " registró "
                + registro.getHoras().stripTrailingZeros().toPlainString() + " horas ("
                + registro.getRecargo().getNombre().toLowerCase() + ") de "
                + registro.getColaborador().getNombre() + " el " + registro.getFecha()
                + ". Entra a School_Plus para aprobarlas o denegarlas.";

        for (Usuario supervisor : supervisores) {
            enviar(supervisor, "Horas extra por resolver — School_Plus", "Pendiente de tu decisión", cuerpo);
            notificacionService.crear(supervisor.getId(), "HORAS_EXTRA_PENDIENTE",
                    "Horas extra por resolver", cuerpo, RUTA_BANDEJA_SUPERVISOR);
        }
    }

    /** Al coordinador que las registró: el supervisor ya decidió. */
    @Async
    public void avisarDecision(RegistroHorasExtra registro) {
        Usuario coordinador = registro.getRegistradoPor();
        boolean aprobado = registro.getEstado() == EstadoHorasExtra.APROBADO;
        String titulo = aprobado
                ? "Se aprobaron las horas extra que registraste"
                : "Se denegaron las horas extra que registraste";

        String cuerpo = registro.getColaborador().getNombre() + " — "
                + registro.getHoras().stripTrailingZeros().toPlainString() + " horas ("
                + registro.getRecargo().getNombre().toLowerCase() + ") del " + registro.getFecha() + ". "
                + (aprobado || registro.getMotivoRechazo() == null || registro.getMotivoRechazo().isBlank()
                        ? "" : "Motivo: " + registro.getMotivoRechazo());

        enviar(coordinador, titulo + " — School_Plus", "Horas extra", cuerpo);
        notificacionService.crear(coordinador.getId(), "HORAS_EXTRA_DECISION", titulo, cuerpo,
                RUTA_MIS_REGISTROS);
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
