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
 * Una toma de asistencia: el profesor la abre, la pantalla muestra el QR y el código, y
 * al cerrarla los inscritos que no aparecieron quedan AUSENTE.
 *
 * <p>{@code claseAbiertaId} vale el id de la clase mientras la sesión está abierta y
 * {@code null} cuando se cierra. Parece redundante con {@code clase}, pero es lo que hace
 * cumplir "una sola sesión abierta por clase" con un UNIQUE en la base y no con un
 * {@code if} que dos peticiones simultáneas podrían saltarse (ver V25).</p>
 */
@Entity
@Table(name = "asistencia_sesion")
@Getter
@Setter
public class AsistenciaSesion {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private Clase clase;

    /** Quién la abrió: el profesor de la clase o un administrador. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profesor_id", nullable = false)
    private Usuario profesor;

    /** Franja del horario elegida al abrir. NULL si la clase fue fuera de horario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_id")
    private ClaseHorario horario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 10, nullable = false)
    private EstadoSesionAsistencia estado = EstadoSesionAsistencia.ABIERTA;

    @Column(name = "abierta_en", nullable = false)
    private LocalDateTime abiertaEn;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** Desde cuándo se mide la tardanza. Se congela al abrir y no cambia después. */
    @Column(name = "hora_referencia", nullable = false)
    private LocalDateTime horaReferencia;

    @Column(name = "tolerancia_minutos", nullable = false)
    private int toleranciaMinutos;

    @Column(name = "clase_abierta_id", length = 36)
    private String claseAbiertaId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean estaAbierta() {
        return estado == EstadoSesionAsistencia.ABIERTA;
    }

    /** Hasta aquí se llega PRESENTE; después, TARDE. */
    public LocalDateTime limiteDePuntualidad() {
        return horaReferencia.plusMinutes(toleranciaMinutos);
    }

    /** Qué estado le corresponde a quien se registra en este instante. */
    public EstadoAsistencia estadoSegunLaHora(LocalDateTime momento) {
        return momento.isAfter(limiteDePuntualidad()) ? EstadoAsistencia.TARDE : EstadoAsistencia.PRESENTE;
    }

    public void cerrar(LocalDateTime momento) {
        this.estado = EstadoSesionAsistencia.CERRADA;
        this.cerradaEn = momento;
        // Libera el candado del UNIQUE: la clase puede volver a abrir sesión.
        this.claseAbiertaId = null;
    }
}
