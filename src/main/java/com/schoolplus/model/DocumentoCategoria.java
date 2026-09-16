package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Catálogo de la migración V5: contrato, hoja de vida, certificados... */
@Entity
@Table(name = "documento_categoria")
@Getter
@Setter
public class DocumentoCategoria {

    @Id
    @Column(name = "codigo", length = 40, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "clase", length = 20, nullable = false)
    private ClaseDocumento clase;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;
}
