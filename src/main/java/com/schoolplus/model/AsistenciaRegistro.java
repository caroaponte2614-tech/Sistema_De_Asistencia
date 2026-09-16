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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que pasó con un estudiante en una sesión: una fila por persona y sesión, y solo una
 * (restricción única en V25).
 *
 * <p>Guarda con qué método entró, desde qué celular, IP y agente, y —si la validación por
 * ubicación está encendida— dónde estaba. Es la evidencia con la que se resuelve un
 * reclamo tres semanas después.</p>
 */
@Entity
@Table(name = "asistencia_registro")
@Getter
@Setter
public class AsistenciaRegistro {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesion_id", nullable = false)
    private AsistenciaSesion sesion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Usuario estudiante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 12, nullable = false)
    private EstadoAsistencia estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", length = 10, nullable = false)
    private MetodoAsistencia metodo;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id")
    private AsistenciaDispositivo dispositivo;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "latitud", precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(name = "longitud", precision = 9, scale = 6)
    private BigDecimal longitud;

    /** Motivo de la justificación o de la corrección manual. */
    @Column(name = "observacion", length = 500)
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modificado_por_id")
    private Usuario modificadoPor;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** ¿Lo tocó una persona después de que el sistema lo creó? */
    public boolean fueCorregido() {
        return modificadoEn != null;
    }
}
