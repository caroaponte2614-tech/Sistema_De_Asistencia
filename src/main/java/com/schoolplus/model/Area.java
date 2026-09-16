package com.schoolplus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Unidad del organigrama. Su coordinador es quien aprueba en primera instancia las
 * solicitudes de la gente del área (§4.4 del prompt).
 *
 * <p>El coordinador se guarda como id suelto y no como {@code @ManyToOne}: área y usuario
 * se referencian mutuamente, y la relación que manda para las aprobaciones es
 * {@code usuario.coordinador_id}. Mantener las dos direcciones como asociaciones JPA solo
 * añadiría un ciclo de carga sin aportar nada.</p>
 */
@Entity
@Table(name = "area")
@Getter
@Setter
public class Area {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "coordinador_id", length = 36)
    private String coordinadorId;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
