package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bitácora de accesos. Inmutable: se inserta y no se toca más.
 *
 * <p>El usuario se guarda como id suelto en vez de {@code @ManyToOne}: hay filas sin
 * usuario (intentos contra correos que no existen) y borrar una cuenta no debe arrastrar
 * su historial de accesos.</p>
 */
@Entity
@Table(name = "auditoria_acceso")
@Getter
@Setter
public class AuditoriaAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "usuario_id", length = 36)
    private String usuarioId;

    @Column(name = "email_intentado", length = 180)
    private String emailIntentado;

    @Enumerated(EnumType.STRING)
    @Column(name = "evento", length = 30, nullable = false)
    private EventoAcceso evento;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "detalle", length = 255)
    private String detalle;

    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;
}
