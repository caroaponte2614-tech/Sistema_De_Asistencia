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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Horas extra de un colaborador en un día, registradas por su coordinador (§4.6).
 *
 * <p>{@code colaborador} es quien trabajó las horas; {@code registradoPor} es quien las
 * cargó —siempre el coordinador de {@code colaborador}, nunca la misma persona—. La
 * entidad no decide su propio estado: eso lo hace {@code services.horasextra.HorasExtraService},
 * igual que {@link Solicitud} con su workflow.</p>
 */
@Entity
@Table(name = "horas_extra_registro")
@Getter
@Setter
public class RegistroHorasExtra {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recargo_codigo", nullable = false)
    private RecargoTipo recargo;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "horas", nullable = false, precision = 5, scale = 2)
    private BigDecimal horas;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoHorasExtra estado = EstadoHorasExtra.PENDIENTE;

    @Column(name = "observacion", length = 300)
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_id")
    private Usuario decididoPor;

    @Column(name = "decidido_en")
    private LocalDateTime decididoEn;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    /** Contraste opcional con el biométrico (§4.6). NULL si no hay integración. */
    @Column(name = "horas_biometrico", precision = 5, scale = 2)
    private BigDecimal horasBiometrico;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
