package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una clase: una asignatura dictada por un docente a un grupo, en un periodo.
 *
 * <p>No se confunde con {@link Curso}, que es una capacitación del personal (video más
 * quiz). Lo escolar y lo laboral conviven sin mezclarse: el docente es un {@link Usuario}
 * con sus permisos y sus vacaciones, y además es el profesor de estas clases.</p>
 *
 * <p>El profesor es quien puede abrir y cerrar sus sesiones de asistencia; la matriz de
 * permisos lo expresa con el ámbito PROPIO de {@code ASISTENCIA_TOMAR}.</p>
 */
@Entity
@Table(name = "clase")
@Getter
@Setter
public class Clase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "asignatura", length = 120, nullable = false)
    private String asignatura;

    @Column(name = "grupo", length = 40)
    private String grupo;

    @Column(name = "periodo", length = 20, nullable = false)
    private String periodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profesor_id", nullable = false)
    private Usuario profesor;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    /** Bloqueo optimista (D-020): la dicta una persona, pero la administra otra. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** "Matemáticas 7A · 2026-2" — para listas y reportes. */
    public String etiqueta() {
        return nombre + " · " + periodo;
    }
}
