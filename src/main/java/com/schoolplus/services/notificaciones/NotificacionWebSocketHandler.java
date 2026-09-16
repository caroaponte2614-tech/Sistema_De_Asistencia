package com.schoolplus.services.notificaciones;

import com.schoolplus.model.Notificacion;
import com.schoolplus.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * El extremo WebSocket de la campanita: una conexión cruda (sin STOMP, sin bróker) que
 * envía objetos JSON de {@link Notificacion} tal cual.
 *
 * <p>Sin STOMP a propósito: montar un bróker de mensajería para una campanita de una
 * sola cola por persona es complejidad que no se paga sola a esta escala (máximo 100
 * usuarios síncronos), y traería una librería cliente (stomp.js) que la CSP de
 * {@code SecurityConfig} —sin ningún CDN permitido para scripts— obligaría a empaquetar
 * a mano. Un {@link org.springframework.web.socket.WebSocketHandler} plano con el
 * {@code WebSocket} nativo del navegador resuelve lo mismo con mucho menos.</p>
 *
 * <p>Quién es el dueño de cada sesión se resuelve UNA vez, al conectar, a partir del
 * {@link Principal} que ya trae el handshake (la sesión HTTP autenticada lo puebla solo:
 * {@code SecurityConfig} exige autenticación en {@code /ws/**}). No hace falta un
 * segundo login dentro del socket.</p>
 */
@Component
public class NotificacionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificacionWebSocketHandler.class);

    /** Varias pestañas o dispositivos de la misma persona, todas conectadas a la vez. */
    private final Map<String, Set<WebSocketSession>> sesionesPorUsuario = new ConcurrentHashMap<>();

    private final UsuarioRepository usuarioRepository;

    public NotificacionWebSocketHandler(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String usuarioId = idDelPrincipal(session.getPrincipal());
        if (usuarioId == null) {
            cerrar(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put("usuarioId", usuarioId);
        sesionesPorUsuario.computeIfAbsent(usuarioId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String usuarioId = (String) session.getAttributes().get("usuarioId");
        if (usuarioId == null) {
            return;
        }
        Set<WebSocketSession> sesiones = sesionesPorUsuario.get(usuarioId);
        if (sesiones != null) {
            sesiones.remove(session);
            if (sesiones.isEmpty()) {
                sesionesPorUsuario.remove(usuarioId, sesiones);
            }
        }
    }

    /** Empuja la notificación a todas las sesiones abiertas de esa persona. Ninguna si no hay. */
    public void enviarA(String usuarioId, Notificacion notificacion) {
        Set<WebSocketSession> sesiones = sesionesPorUsuario.get(usuarioId);
        if (sesiones == null || sesiones.isEmpty()) {
            return;
        }
        TextMessage mensaje = new TextMessage(aJson(notificacion));
        for (WebSocketSession sesion : sesiones) {
            try {
                if (sesion.isOpen()) {
                    sesion.sendMessage(mensaje);
                }
            } catch (IOException e) {
                log.warn("No se pudo entregar una notificación en vivo a {}: {}", usuarioId, e.getMessage());
            }
        }
    }

    private void cerrar(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignorado) {
            // La sesión ya se estaba cerrando por su cuenta; no hay nada más que hacer.
        }
    }

    /**
     * JSON a mano y no con {@code ObjectMapper}: Spring Boot 4 autoconfigura el bean
     * sobre {@code tools.jackson} (Jackson 3), no sobre el {@code com.fasterxml.jackson}
     * clásico que trae {@code spring-boot-starter-web} en el classpath — inyectar el tipo
     * equivocado revienta el arranque (hallazgo del 2026-09-04). El payload es fijo y
     * chico: seis campos, ninguno anidado. No vale la pena resolver esa ambigüedad de
     * versión de Jackson por esto.
     */
    private String aJson(Notificacion n) {
        return "{"
                + "\"id\":" + cadena(n.getId()) + ","
                + "\"tipo\":" + cadena(n.getTipo()) + ","
                + "\"titulo\":" + cadena(n.getTitulo()) + ","
                + "\"cuerpo\":" + cadena(n.getCuerpo()) + ","
                + "\"enlace\":" + cadena(n.getEnlace()) + ","
                + "\"leida\":" + n.isLeida() + ","
                + "\"creadoEn\":" + cadena(n.getCreadoEn() == null ? null : n.getCreadoEn().toString())
                + "}";
    }

    private String cadena(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder escapado = new StringBuilder(valor.length() + 2).append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> escapado.append("\\\"");
                case '\\' -> escapado.append("\\\\");
                case '\n' -> escapado.append("\\n");
                case '\r' -> escapado.append("\\r");
                case '\t' -> escapado.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escapado.append(String.format("\\u%04x", (int) c));
                    } else {
                        escapado.append(c);
                    }
                }
            }
        }
        return escapado.append('"').toString();
    }

    private String idDelPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }
        return usuarioRepository.findByEmail(principal.getName()).map(u -> u.getId()).orElse(null);
    }
}
