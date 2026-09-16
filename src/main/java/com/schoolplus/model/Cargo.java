package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cargo del colaborador.
 *
 * <p>Origen del catálogo: un proyecto de referencia (services/auth/ValidationService.java)
 * ({@code CARGOS_VALIDOS}).</p>
 */
@Entity
@Table(name = "cargo")
@Getter
@Setter
public class Cargo {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
