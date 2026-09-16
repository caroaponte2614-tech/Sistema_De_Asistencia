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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * El progreso de un usuario sobre un {@link Curso}: si ya vio el video, cuántas veces
 * presentó el quiz y su mejor nota. Una fila por (curso, usuario) — se crea la primera
 * vez que alguien abre el curso, no antes.
 */
@Entity
@Table(name = "curso_inscripcion")
@Getter
@Setter
public class CursoInscripcion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "video_completado_en")
    private LocalDateTime videoCompletadoEn;

    @Column(name = "quiz_aprobado", nullable = false)
    private boolean quizAprobado;

    @Column(name = "mejor_nota", precision = 5, scale = 2)
    private BigDecimal mejorNota;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;
}
