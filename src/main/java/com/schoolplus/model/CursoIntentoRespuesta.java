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

/** Qué opción eligió el usuario para una pregunta, dentro de un {@link CursoIntento}. */
@Entity
@Table(name = "curso_intento_respuesta")
@Getter
@Setter
public class CursoIntentoRespuesta {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intento_id", nullable = false)
    private CursoIntento intento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private CursoPregunta pregunta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opcion_elegida_id")
    private CursoOpcion opcionElegida;

    @Column(name = "correcta", nullable = false)
    private boolean correcta;
}
