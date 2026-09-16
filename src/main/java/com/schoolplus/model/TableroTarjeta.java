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
 * Una tarjeta del tablero.
 *
 * <p>Origen: un proyecto de referencia (model/TareaTablero.java). Dos cosas que allí no había y
 * aquí sí:</p>
 * <ul>
 *   <li>{@code orden}: el original ordenaba en memoria por prioridad y fecha, así que con
 *       drag &amp; drop arrastrar una tarjeta no sobrevivía a recargar la página.</li>
 *   <li>{@code @Version}: mover tarjetas es justo el caso donde dos pestañas abiertas se
 *       pisan. Con bloqueo optimista, la segunda recibe 409 y recarga en vez de deshacer
 *       en silencio el movimiento de la primera (§5.2).</li>
 * </ul>
 */
@Entity
@Table(name = "tablero_tarjeta")
@Getter
@Setter
public class TableroTarjeta {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "columna_id", nullable = false)
    private TableroColumna columna;

    @Column(name = "titulo", length = 200, nullable = false)
    private String titulo;

    /** El campo `area` del original: etiqueta libre de área o proyecto. */
    @Column(name = "etiqueta", length = 120)
    private String etiqueta;

    @Column(name = "nota", columnDefinition = "TEXT")
    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", length = 20, nullable = false)
    private PrioridadTarea prioridad = PrioridadTarea.NORMAL;

    /** Vencimiento opcional, con hora. NULL = sin fecha límite. */
    @Column(name = "fecha_limite")
    private LocalDateTime fechaLimite;

    /** Si {@code TableroRecordatorioJob} ya avisó del vencimiento de {@link #fechaLimite}. */
    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado;

    /** Posición dentro de la columna. Lo que hace que el arrastre se guarde. */
    @Column(name = "orden", nullable = false)
    private int orden;

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
