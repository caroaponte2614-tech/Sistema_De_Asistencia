package com.schoolplus.configuration;

import com.schoolplus.services.notificaciones.NotificacionWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * El socket de la campanita, en {@code /ws/notificaciones}. Va sobre WebSocket puro
 * (ver el javadoc de {@link NotificacionWebSocketHandler} para el porqué de no usar
 * STOMP), y {@code SecurityConfig} lo exige autenticado igual que cualquier otra ruta:
 * no hay matcher especial para {@code /ws/**}, cae en {@code anyRequest().authenticated()}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificacionWebSocketHandler notificacionWebSocketHandler;
    private final String origenPermitido;

    public WebSocketConfig(NotificacionWebSocketHandler notificacionWebSocketHandler,
                           @Value("${app.base-url}") String origenPermitido) {
        this.notificacionWebSocketHandler = notificacionWebSocketHandler;
        this.origenPermitido = origenPermitido;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificacionWebSocketHandler, "/ws/notificaciones")
                .setAllowedOrigins(origenPermitido);
    }
}
