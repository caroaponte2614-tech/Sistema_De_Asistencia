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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una evaluación de desempeño. Solo-agregar: una evaluación pasada no se edita ni se
 * borra, es un hecho histórico (mismo espíritu que {@code DocumentoVersion}).
 *
 * <p>{@code calificacion} queda como texto libre corto porque todavía no hay una
 * rúbrica de desempeño definida — si Talento Humano define una escala fija, se agrega
 * un CHECK o un enum sin romper lo existente.</p>
 */
@Entity
@Table(name = "perfil_evaluacion")
@Getter
@Setter
public class PerfilEvaluacion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "calificacion", nullable = false, length = 50)
    private String calificacion;

    @Column(name = "comentario", length = 2000)
    private String comentario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluado_por_id", nullable = false)
    private Usuario evaluadoPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
