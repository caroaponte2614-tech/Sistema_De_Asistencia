package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Una acción que un rol puede o no puede ejecutar. Catálogo cargado por la migración V1. */
@Entity
@Table(name = "capacidad")
@Getter
@Setter
public class Capacidad {

    @Id
    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    @Column(name = "descripcion", length = 255, nullable = false)
    private String descripcion;

    @Column(name = "modulo", length = 40, nullable = false)
    private String modulo;
}
