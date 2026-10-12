package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Rol del sistema. Son los cuatro del §3 del prompt: ADMIN, SUPERVISOR, COORDINADOR y
 * USUARIO, cargados por la migración V1.
 *
 * <p>Es una entidad y no un {@code enum} —como sí lo era en los tres repos de origen—
 * porque la matriz de permisos vive en la base de datos ({@link RolCapacidad}) y necesita
 * una clave foránea real contra la que apoyarse.</p>
 */
@Entity
@Table(name = "rol")
@Getter
@Setter
public class Rol {

    /** Códigos con nombre, para no repetir literales por todo el código. */
    public static final String ADMIN = "ADMIN";
    public static final String SUPERVISOR = "SUPERVISOR";
    public static final String COORDINADOR = "COORDINADOR";
    public static final String USUARIO = "USUARIO";

    /**
     * Estudiante matriculado. Es el único rol que no es "personal de la institución": no
     * toma asistencia ni administra nada — solo registra la suya.
     */
    public static final String ESTUDIANTE = "ESTUDIANTE";

    @Id
    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}
