package com.schoolplus.services.asistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * El socket de la pantalla de proyección: avisa a quien la tenga abierta de que algo
 * cambió en la sesión.
 *
 * <p>Mismo patrón que la campanita de notificaciones —WebSocket crudo, sin STOMP—, con dos
 * diferencias: aquí lo que se agrupa es la <em>sesión de asistencia</em>, no la persona, y
 * el mensaje no lleva datos. Solo dice "cambió"; la pantalla vuelve a pedir el estado a la
 * API, que es la misma llamada que ya hace cada vez que el QR rota. Así no hay dos
 * caminos por los que pintar la lista y no pueden divergir.</p>
 *
 * <p>Quién puede escuchar qué sesión se decide en el apretón de manos
 * ({@link AsistenciaHandshakeInterceptor}), donde todavía hay contexto de seguridad.</p>
 */
@Component
public class AsistenciaEnVivoHandler extends TextWebSocketHandler {

    /** Clave con la que el interceptor deja el id de la sesión en los atributos del socket. */
    public static final String ATRIBUTO_SESION = "asistenciaSesionId";

    private static final Logger log = LoggerFactory.getLogger(AsistenciaEnVivoHandler.class);
    private static final TextMessage AVISO = new TextMessage("{\"tipo\":\"cambio\"}");

    private final Map<String, Set<WebSocketSession>> pantallasPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) {
        String sesionId = (String) socket.getAttributes().get(ATRIBUTO_SESION);
        if (sesionId == null) {
            cerrar(socket);
            return;
        }
        pantallasPorSesion.computeIfAbsent(sesionId, id -> new CopyOnWriteArraySet<>()).add(socket);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus estado) {
        String sesionId = (String) socket.getAttributes().get(ATRIBUTO_SESION);
        if (sesionId == null) {
            return;
        }
        Set<WebSocketSession> pantallas = pantallasPorSesion.get(sesionId);
        if (pantallas != null) {
            pantallas.remove(socket);
            if (pantallas.isEmpty()) {
                pantallasPorSesion.remove(sesionId, pantallas);
            }
        }
    }

    /**
     * Avisa a las pantallas abiertas de esa sesión. Se llama DESPUÉS del commit, nunca
     * dentro de la transacción: el §5.2 prohíbe transacciones abiertas durante llamadas de
     * red, y además una pantalla no debe enterarse de algo que todavía podría deshacerse.
     */
    public void avisar(String sesionId) {
        Set<WebSocketSession> pantallas = pantallasPorSesion.get(sesionId);
        if (pantallas == null || pantallas.isEmpty()) {
            return;
        }
        for (WebSocketSession pantalla : pantallas) {
            try {
                if (pantalla.isOpen()) {
                    pantalla.sendMessage(AVISO);
                }
            } catch (IOException e) {
                log.warn("No se pudo avisar a una pantalla de la sesión {}: {}", sesionId, e.getMessage());
            }
        }
    }

    private void cerrar(WebSocketSession socket) {
        try {
            socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignorado) {
            // Ya se estaba cerrando por su cuenta.
        }
    }
}
