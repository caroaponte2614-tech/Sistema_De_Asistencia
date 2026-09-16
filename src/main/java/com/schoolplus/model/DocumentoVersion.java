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
 * Un archivo subido. {@code almacenamientoClave} es la clave relativa del archivo en el almacenamiento local: el
 * contenido nunca toca el disco del app server (§5.3, comentario de cabecera de V5).
 */
@Entity
@Table(name = "documento_version")
@Getter
@Setter
public class DocumentoVersion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @Column(name = "numero", nullable = false)
    private int numero;

    @Column(name = "nombre_archivo", length = 255, nullable = false)
    private String nombreArchivo;

    /** MIME real, detectado por bytes, no el declarado por el navegador (§4.2). */
    @Column(name = "mime", length = 120, nullable = false)
    private String mime;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "almacenamiento_clave", length = 400, nullable = false)
    private String almacenamientoClave;

    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subido_por_id")
    private Usuario subidoPor;

    @Column(name = "subido_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime subidoEn;
}
