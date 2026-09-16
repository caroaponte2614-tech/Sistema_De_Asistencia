package com.schoolplus.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un tablero de tareas. Puede ser de UNA persona (§4.3) o de UN área (D-030): dueño
 * exclusivo, nunca los dos a la vez ni ninguno — eso lo hace cumplir {@code ck_tablero_dueno}
 * en la V24, y {@link com.schoolplus.services.tablero.TableroService} nunca crea un
 * tablero de otra forma.
 *
 * <p>Origen del módulo: un proyecto de referencia (`TareaTablero`, `TableroService`,
 * `TableroController`). Allí el tablero era uno solo y exclusivo del rol ADMIN; aquí cada
 * usuario tiene el suyo (personal, nadie más lo ve), y desde la V24 cada área tiene también
 * el suyo (compartido con su equipo, lo gestiona su líder).</p>
 */
@Entity
@Table(name = "tablero")
@Getter
@Setter
public class Tablero {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    /** NULL cuando este tablero es de un área, no de una persona. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** NULL cuando este tablero es personal, no de un área. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre = "Mi tablero";

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "tablero", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<TableroColumna> columnas = new ArrayList<>();
}
