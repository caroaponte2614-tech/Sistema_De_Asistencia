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
 * Una reacción a una publicación o a un comentario, nunca a las dos.
 *
 * <p>No lleva {@code @Version} y es deliberado: reaccionar es poner o quitar una fila
 * entera, no modificar una existente. La concurrencia la resuelve la restricción única
 * (una reacción por persona y objeto), no el bloqueo optimista.</p>
 */
@Entity
@Table(name = "reaccion")
@Getter
@Setter
public class Reaccion {

    /** Único tipo por ahora. La columna admite más sin migración. */
    public static final String ME_GUSTA = "ME_GUSTA";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publicacion_id")
    private Publicacion publicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comentario_id")
    private Comentario comentario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "tipo", length = 20, nullable = false)
    private String tipo = ME_GUSTA;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
