package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una línea del historial de una solicitud. Inmutable (§4.4).
 *
 * <p>No lleva {@code version} ni {@code actualizado_en} a propósito: se inserta y no se
 * toca nunca más. Es el registro de quién movió qué, cuándo y por qué.</p>
 */
@Entity
@Table(name = "solicitud_historial")
@Getter
@Setter
public class SolicitudHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    /** NULL en la creación: no venía de ningún estado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_origen", length = 30)
    private EstadoSolicitud estadoOrigen;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_destino", length = 30, nullable = false)
    private EstadoSolicitud estadoDestino;

    /** NULL si lo movió el sistema y no una persona. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Usuario actor;

    @Column(name = "comentario", length = 500)
    private String comentario;

    @Column(name = "ocurrido_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime ocurridoEn;
}
