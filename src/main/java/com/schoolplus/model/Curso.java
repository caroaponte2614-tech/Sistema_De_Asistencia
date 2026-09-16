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

/**
 * Un curso: un video más, opcionalmente, un quiz (§ capacitaciones). No hay lecciones
 * múltiples en esta primera versión — un curso es una sola pieza de video.
 *
 * <p>La inducción no es una entidad distinta: es un {@code Curso} con
 * {@code tipo = INDUCCION}. Ver {@link TipoCurso}.</p>
 */
@Entity
@Table(name = "curso")
@Getter
@Setter
public class Curso {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoCurso tipo = TipoCurso.CAPACITACION;

    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    @Column(name = "video_clave", nullable = false, length = 500)
    private String videoClave;

    @Column(name = "video_mime", length = 100)
    private String videoMime;

    @Column(name = "video_tamano_bytes")
    private Long videoTamanoBytes;

    @Column(name = "miniatura_clave", length = 500)
    private String miniaturaClave;

    @Column(name = "publicado", nullable = false)
    private boolean publicado;

    @Column(name = "orden")
    private Integer orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
