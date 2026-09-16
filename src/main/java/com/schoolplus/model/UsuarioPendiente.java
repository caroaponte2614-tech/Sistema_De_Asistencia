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
 * Cuenta solicitada por auto-registro y todavía sin verificar.
 *
 * <p>Origen: un proyecto de referencia (model/UsuarioPendiente.java) — el único de los tres repos
 * donde el auto-registro sigue vivo. Se conserva el patrón entero: fila aparte con token de
 * 24 horas, que solo se promueve a {@link Usuario} cuando la persona abre el enlace.</p>
 */
@Entity
@Table(name = "usuario_pendiente")
@Getter
@Setter
public class UsuarioPendiente {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "email", length = 180, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "cedula", length = 20)
    private String cedula;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private Sede sede;

    @Column(name = "token", length = 100, nullable = false)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    public boolean estaVencido() {
        return expiraEn.isBefore(LocalDateTime.now());
    }
}
