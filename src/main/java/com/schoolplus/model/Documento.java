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
 * La identidad estable de un documento (§4.2, V5). Los archivos en sí son
 * {@link DocumentoVersion}: subir de nuevo no sobrescribe, agrega versión.
 */
@Entity
@Table(name = "documento")
@Getter
@Setter
public class Documento {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    /** De quién es el expediente/carpeta. No necesariamente quien lo sube. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_codigo")
    private DocumentoCategoria categoria;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad", length = 20, nullable = false)
    private VisibilidadDocumento visibilidad = VisibilidadDocumento.PRIVADO;

    @Column(name = "version_actual", nullable = false)
    private int versionActual = 0;

    @Column(name = "archivado", nullable = false)
    private boolean archivado = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean esRestringido() {
        return categoria.getClase() == ClaseDocumento.RESTRINGIDO;
    }
}
