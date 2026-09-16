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
 * El voto de una persona en una encuesta.
 *
 * <p>Guarda la publicación además de la opción, y no es redundancia: es lo que sostiene la
 * restricción única (publicacion_id, usuario_id). Colgando solo de la opción, alguien
 * podría votar todas las opciones de la misma encuesta.</p>
 */
@Entity
@Table(name = "encuesta_voto")
@Getter
@Setter
public class EncuestaVoto {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacion_id", nullable = false)
    private Publicacion publicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opcion_id", nullable = false)
    private EncuestaOpcion opcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "votado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime votadoEn;
}
