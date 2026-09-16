package com.schoolplus.services.notificaciones;

import com.schoolplus.model.Notificacion;

import java.time.LocalDateTime;

/** Lo que viaja por WebSocket o por la API REST: nunca la entidad JPA tal cual. */
public record NotificacionPayload(String id, String tipo, String titulo, String cuerpo,
                                  String enlace, boolean leida, LocalDateTime creadoEn) {

    public static NotificacionPayload de(Notificacion n) {
        return new NotificacionPayload(n.getId(), n.getTipo(), n.getTitulo(), n.getCuerpo(),
                n.getEnlace(), n.isLeida(), n.getCreadoEn());
    }
}
