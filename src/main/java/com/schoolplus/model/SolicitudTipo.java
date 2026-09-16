package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tipo de solicitud. Es lo que parametriza el motor de aprobación (§4.5).
 *
 * <p>Los cinco tipos de permiso del §4.4 y las vacaciones son filas de esta tabla, no
 * ramas del código: añadir un sexto tipo es un {@code INSERT}.</p>
 */
@Entity
@Table(name = "solicitud_tipo")
@Getter
@Setter
public class SolicitudTipo {

    public static final String VACACIONES = "VACACIONES";

    @Id
    @Column(name = "codigo", length = 40, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "familia", length = 20, nullable = false)
    private FamiliaSolicitud familia;

    /** Solo las vacaciones descuentan saldo. Es lo que dispara todo el cálculo. */
    @Column(name = "descuenta_saldo", nullable = false)
    private boolean descuentaSaldo;

    @Column(name = "requiere_soporte", nullable = false)
    private boolean requiereSoporte;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
