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
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Una columna del tablero.
 *
 * <p>Los cuatro códigos vienen del enum {@code ColumnaTablero} de un proyecto de referencia, pero
 * aquí son filas: en el original cambiar una etiqueta obligaba a recompilar.</p>
 */
@Entity
@Table(name = "tablero_columna")
@Getter
@Setter
public class TableroColumna {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CURSO = "CURSO";
    public static final String ESPERA = "ESPERA";
    public static final String HECHO = "HECHO";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tablero_id", nullable = false)
    private Tablero tablero;

    @Column(name = "codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(name = "orden", nullable = false)
    private int orden;

    @OneToMany(mappedBy = "columna", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<TableroTarjeta> tarjetas = new ArrayList<>();
}
