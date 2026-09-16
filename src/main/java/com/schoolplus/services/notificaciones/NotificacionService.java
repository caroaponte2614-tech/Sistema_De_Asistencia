package com.schoolplus.services.notificaciones;

import com.schoolplus.model.Notificacion;
import com.schoolplus.repository.NotificacionRepository;
import com.schoolplus.util.UuidV7;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La campanita: crea la notificación, la deja guardada, y si esa persona tiene una
 * pestaña abierta ahora mismo se la empuja por WebSocket.
 *
 * <p>Se llama desde cuatro sitios (D-025): {@code NotificadorSolicitudes} (pendiente y
 * decidida), {@code MuroService} (comentario y reacción sobre lo tuyo),
 * {@code DocumentoService} (documento restringido nuevo) y
 * {@code TableroRecordatorioJob} (vencimiento). Ninguno de los cuatro depende de que el
 * WebSocket esté conectado: la fila en {@code notificacion} es la fuente de verdad, el
 * socket es solo el aviso en vivo.</p>
 */
@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final NotificacionWebSocketHandler webSocketHandler;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               NotificacionWebSocketHandler webSocketHandler) {
        this.notificacionRepository = notificacionRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Crea y empuja. No es {@code @Async} a propósito, a diferencia del correo de
     * {@code NotificadorSolicitudes}: guardar una fila es rápido y no vale la pena la
     * complejidad de otro hilo por esto. Quien llama decide si el suyo se hace async.
     */
    @Transactional
    public void crear(String usuarioId, String tipo, String titulo, String cuerpo, String enlace) {
        if (usuarioId == null) {
            return;
        }
        Notificacion notificacion = new Notificacion();
        notificacion.setId(UuidV7.nuevoId());
        notificacion.setUsuarioId(usuarioId);
        notificacion.setTipo(tipo);
        notificacion.setTitulo(titulo);
        notificacion.setCuerpo(cuerpo);
        notificacion.setEnlace(enlace);
        notificacionRepository.save(notificacion);

        webSocketHandler.enviarA(usuarioId, notificacion);
    }

    @Transactional(readOnly = true)
    public Page<Notificacion> recientesDe(String usuarioId, int cantidad) {
        return notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(
                usuarioId, PageRequest.of(0, cantidad));
    }

    @Transactional(readOnly = true)
    public long noLeidasDe(String usuarioId) {
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    /** Silenciosamente no hace nada si la notificación no existe o no es de esa persona. */
    @Transactional
    public void marcarLeida(String usuarioId, String notificacionId) {
        notificacionRepository.marcarLeida(notificacionId, usuarioId);
    }

    @Transactional
    public void marcarTodasLeidas(String usuarioId) {
        notificacionRepository.marcarTodasLeidas(usuarioId);
    }
}
