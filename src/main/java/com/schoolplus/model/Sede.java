package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Sede o punto de venta.
 *
 * <p>Origen del catálogo: un proyecto de referencia (services/auth/ValidationService.java)
 * ({@code PUNTOS_VALIDOS}), donde era un {@code Set.of()} inmutable en el código. Aquí es
 * una tabla, cargada por la migración V1, para que Talento Humano pueda mantenerla.</p>
 */
@Entity
@Table(name = "sede")
@Getter
@Setter
public class Sede {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "ciudad", length = 120)
    private String ciudad;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
