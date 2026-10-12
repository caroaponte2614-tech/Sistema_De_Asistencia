package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Persona de la institución (docente, estudiante o personal). Es la identidad canónica de School_Plus.
 *
 * <p>Origen: un proyecto de referencia (model/Usuario.java) y un proyecto de referencia (model/Usuario.java).
 * Tres diferencias deliberadas con el original (ver docs/DECISIONES.md):</p>
 * <ul>
 *   <li>La columna es {@code password_hash} y no {@code contraseña}: los tres repos de
 *       origen tienen una eñe en un nombre de columna. Se renombra al migrar.</li>
 *   <li>Los roles son una relación N:M y no un {@code enum} de un solo valor: la matriz de
 *       permisos del §3.1 admite que una persona acumule roles.</li>
 *   <li>{@code password_hash} admite NULL. Es el estado de un usuario migrado desde un
 *       sistema cuyo hash no se pudo reutilizar: entra por "recordar contraseña" (§6.2).</li>
 * </ul>
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "email", length = 180, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "cedula", length = 20)
    private String cedula;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    /** Opcional. */
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    /** Bloqueo optimista (§5.2). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Se carga con avidez a propósito: {@code UsuarioDetailsService} necesita los roles en
     * cada autenticación y {@code open-in-view} está apagado.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_codigo"))
    private Set<Rol> roles = new HashSet<>();

    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    /** Un usuario migrado sin hash utilizable no puede autenticarse con contraseña. */
    public boolean tienePasswordUtilizable() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
