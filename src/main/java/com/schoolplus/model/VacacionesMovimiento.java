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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cada suma y cada resta al saldo de vacaciones, con su motivo.
 *
 * <p>Sin esta tabla, un saldo raro no se puede explicar y la única salida es recalcular a
 * mano. Con ella, el saldo siempre es la suma de sus movimientos.</p>
 */
@Entity
@Table(name = "vacaciones_movimiento")
@Getter
@Setter
public class VacacionesMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saldo_id", nullable = false)
    private VacacionesSaldo saldo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoMovimientoVacaciones tipo;

    @Column(name = "dias", nullable = false, precision = 6, scale = 2)
    private BigDecimal dias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    @Column(name = "ocurrido_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime ocurridoEn;
}
