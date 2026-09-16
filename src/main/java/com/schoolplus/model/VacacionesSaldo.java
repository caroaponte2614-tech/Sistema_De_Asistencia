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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saldo de vacaciones de una persona en un periodo: causados, tomados y en trámite (§4.5).
 *
 * <p>Es la fila sobre la que se toma el bloqueo PESIMISTA que autoriza el §5.2. El motivo
 * es concreto: dos solicitudes enviadas a la vez que caben por separado pero no juntas.
 * Con bloqueo optimista, la segunda fallaría con un 409 confuso después de haber
 * comprobado el saldo; con pesimista, espera su turno y ve el saldo ya reservado.</p>
 */
@Entity
@Table(name = "vacaciones_saldo")
@Getter
@Setter
public class VacacionesSaldo {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Año de causación. Lo no tomado se acumula por periodo. */
    @Column(name = "periodo", nullable = false)
    private short periodo;

    @Column(name = "dias_causados", nullable = false, precision = 6, scale = 2)
    private BigDecimal diasCausados = BigDecimal.ZERO;

    @Column(name = "dias_tomados", nullable = false, precision = 6, scale = 2)
    private BigDecimal diasTomados = BigDecimal.ZERO;

    @Column(name = "dias_en_tramite", nullable = false, precision = 6, scale = 2)
    private BigDecimal diasEnTramite = BigDecimal.ZERO;

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Lo que de verdad se puede pedir hoy. */
    public BigDecimal disponible() {
        return diasCausados.subtract(diasTomados).subtract(diasEnTramite);
    }
}
