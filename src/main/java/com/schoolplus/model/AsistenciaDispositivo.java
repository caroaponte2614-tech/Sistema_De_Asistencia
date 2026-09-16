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
 * El celular con el que un estudiante registra asistencia.
 *
 * <p>Se identifica con una cookie aleatoria que pone el servidor (HttpOnly, así que el
 * JavaScript de la página no puede leerla ni copiarla) y de la que aquí solo se guarda el
 * SHA-256: si alguien se lleva la base, no se lleva las cookies.</p>
 *
 * <p>Las dos columnas {@code activo*} sostienen los candados del antifraude con UNIQUE en
 * vez de con código: un estudiante no puede tener dos celulares activos, y un celular no
 * puede estar activo para dos estudiantes. Valen mientras el dispositivo está ACTIVO y
 * quedan en {@code null} en cualquier otro estado.</p>
 */
@Entity
@Table(name = "asistencia_dispositivo")
@Getter
@Setter
public class AsistenciaDispositivo {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Usuario estudiante;

    @Column(name = "identificador_hash", length = 64, nullable = false)
    private String identificadorHash;

    /** User agent resumido: "Android · Chrome". Para que el profesor sepa qué aprueba. */
    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 12, nullable = false)
    private EstadoDispositivo estado;

    @Column(name = "solicitado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime solicitadoEn;

    @Column(name = "vinculado_en")
    private LocalDateTime vinculadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por_id")
    private Usuario resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "activo_estudiante_id", length = 36)
    private String activoEstudianteId;

    @Column(name = "activo_identificador", length = 64)
    private String activoIdentificador;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean estaActivo() {
        return estado == EstadoDispositivo.ACTIVO;
    }

    /** Lo pone en uso y toma los dos candados únicos. */
    public void activar(LocalDateTime momento) {
        this.estado = EstadoDispositivo.ACTIVO;
        this.vinculadoEn = momento;
        this.activoEstudianteId = estudiante.getId();
        this.activoIdentificador = identificadorHash;
    }

    /** Lo saca de uso y suelta los candados, para que otro dispositivo pueda tomarlos. */
    public void desactivar(EstadoDispositivo nuevoEstado, Usuario actor, LocalDateTime momento) {
        this.estado = nuevoEstado;
        this.activoEstudianteId = null;
        this.activoIdentificador = null;
        this.resueltoPor = actor;
        this.resueltoEn = momento;
    }
}
