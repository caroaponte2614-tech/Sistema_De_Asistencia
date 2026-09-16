package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una notificación de la campanita (V15). La crea {@code NotificacionService}, que
 * también la empuja en vivo por WebSocket a quien la tenga conectada — esta fila es la
 * copia persistente: si no hay nadie escuchando en ese momento, sigue estando acá para
 * la próxima vez que la persona entre.
 */
@Entity
@Table(name = "notificacion")
@Getter
@Setter
public class Notificacion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "usuario_id", length = 36, nullable = false)
    private String usuarioId;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    @Column(name = "cuerpo", length = 500)
    private String cuerpo;

    /** A dónde navega el clic. NULL = no navega a ningún sitio en particular. */
    @Column(name = "enlace", length = 300)
    private String enlace;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
