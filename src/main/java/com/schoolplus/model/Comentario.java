package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Comentario de una publicación del muro. */
@Entity
@Table(name = "comentario")
@Getter
@Setter
public class Comentario {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacion_id", nullable = false)
    private Publicacion publicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "cuerpo", length = 2000, nullable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoComentario estado = EstadoComentario.VISIBLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oculto_por_id")
    private Usuario ocultoPor;

    @Column(name = "oculto_en")
    private LocalDateTime ocultoEn;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Bloqueo optimista (§5.2): dos moderadores sobre el mismo comentario. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean estaVisible() {
        return estado == EstadoComentario.VISIBLE;
    }
}
