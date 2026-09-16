package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Catálogo de tipos de recargo de horas extra (diurna, nocturna, dominical, festiva),
 * de la migración V8.
 *
 * <p>{@code factor} queda en {@code NULL} y {@code pendienteConfirmacion} en
 * {@code true} hasta que Talento Humano confirme los porcentajes de recargo: son cifras
 * legales, no se inventan. Mientras tanto el módulo solo registra horas, no calcula
 * pago.</p>
 */
@Entity
@Table(name = "recargo_tipo")
@Getter
@Setter
public class RecargoTipo {

    @Id
    @Column(name = "codigo", length = 40, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "factor", precision = 4, scale = 2)
    private BigDecimal factor;

    @Column(name = "pendiente_confirmacion", nullable = false)
    private boolean pendienteConfirmacion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
