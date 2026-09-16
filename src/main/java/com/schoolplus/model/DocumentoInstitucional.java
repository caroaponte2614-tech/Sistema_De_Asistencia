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
 * Un documento visible para toda la empresa (política, manual, formato) — distinto de
 * {@link Documento}, que es el portafolio personal de cada colaborador. Se muestra en
 * el carrusel del portal y en el listado completo; cada apertura queda registrada en
 * {@link DocumentoInstitucionalVista}, la base del chulo de "leído" y del consolidado.
 */
@Entity
@Table(name = "documento_institucional")
@Getter
@Setter
public class DocumentoInstitucional {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "categoria", length = 100)
    private String categoria;

    @Column(name = "archivo_clave", nullable = false, length = 500)
    private String archivoClave;

    @Column(name = "archivo_mime", length = 100)
    private String archivoMime;

    @Column(name = "archivo_tamano_bytes")
    private Long archivoTamanoBytes;

    @Column(name = "miniatura_clave", length = 500)
    private String miniaturaClave;

    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    @Column(name = "publicado", nullable = false)
    private boolean publicado;

    @Column(name = "archivado", nullable = false)
    private boolean archivado;

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
