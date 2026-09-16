package com.schoolplus.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Una entrada del muro: post, encuesta o reconocimiento (§4.7). */
@Entity
@Table(name = "publicacion")
@Getter
@Setter
public class Publicacion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoPublicacion tipo = TipoPublicacion.POST;

    @Column(name = "titulo", length = 200)
    private String titulo;

    @Column(name = "cuerpo", nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    /** Solo en RECONOCIMIENTO: a quién se reconoce. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinatario_id")
    private Usuario destinatario;

    @Column(name = "fijada", nullable = false)
    private boolean fijada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPublicacion estado = EstadoPublicacion.VISIBLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oculta_por_id")
    private Usuario ocultaPor;

    @Column(name = "oculta_en")
    private LocalDateTime ocultaEn;

    /** Cierre de la encuesta. NULL = abierta indefinidamente. */
    @Column(name = "cierra_en")
    private LocalDateTime cierraEn;

    @Column(name = "publicado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime publicadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    /**
     * Bloqueo optimista (§5.2). El caso real: dos moderadores ocultando la misma
     * publicación, o alguien editando mientras otro la fija. El segundo recibe 409 en vez
     * de pisar el cambio del primero sin enterarse.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "publicacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<EncuestaOpcion> opciones = new ArrayList<>();

    public boolean esEncuesta() {
        return tipo == TipoPublicacion.ENCUESTA;
    }

    public boolean estaVisible() {
        return estado == EstadoPublicacion.VISIBLE;
    }

    /** Una encuesta sin fecha de cierre sigue abierta; con fecha, hasta que llegue. */
    public boolean admiteVotos() {
        return esEncuesta() && estaVisible()
                && (cierraEn == null || cierraEn.isAfter(LocalDateTime.now()));
    }
}
