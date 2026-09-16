package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Token de recuperación de contraseña.
 *
 * <p>Origen: un proyecto de referencia (model/PasswordResetToken.java). Se conserva la vigencia de 30
 * minutos y la regla de un solo token vivo por usuario. Se añade {@code usado_en}: el
 * original borraba la fila al canjearla, lo que dejaba sin rastro si un token se usó dos
 * veces o si alguien pidió enlaces en ráfaga.</p>
 */
@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
public class PasswordResetToken {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token", length = 100, nullable = false)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Un token sirve una sola vez y solo dentro de su ventana de vigencia. */
    public boolean esCanjeable() {
        return usadoEn == null && expiraEn.isAfter(LocalDateTime.now());
    }
}
