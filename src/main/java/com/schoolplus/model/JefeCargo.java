package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Qué cargo está al mando de qué jefe. Es la pieza que RH usa para poblar
 * {@code usuario.coordinador_id} en bloque: "el que tenga el cargo Mensajero, que
 * reporte a Juan Pérez", en vez de asignar el jefe persona por persona.
 *
 * <p>La PK es {@code cargo_id}, no la pareja (cargo, jefe): un cargo cuelga de un solo
 * jefe a la vez (ver comentario de cabecera de V10). Reasignarlo es un UPDATE de esta
 * fila.</p>
 *
 * <p>No es la fuente autoritativa de "quién manda sobre quién" —esa sigue siendo
 * {@code usuario.coordinador_id}, ver {@link Usuario#getCoordinador()}—: es solo el
 * insumo que {@code OrganigramaService} usa para sugerirlo.</p>
 */
@Entity
@Table(name = "jefe_cargo")
@Getter
@Setter
public class JefeCargo {

    /** Mismo id que el cargo gobernado: un cargo, un jefe, sin id propio que administrar. */
    @Id
    @Column(name = "cargo_id", length = 36, nullable = false)
    private String cargoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jefe_id")
    private Usuario jefe;

    @Column(name = "asignado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime asignadoEn;
}
