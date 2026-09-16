package com.schoolplus.model;

/**
 * Prioridad de una tarjeta del tablero.
 *
 * <p>Origen: un proyecto de referencia (model/PrioridadTarea.java). Se conservan sus dos valores
 * y el orden de declaración, que allí era el orden de despliegue.</p>
 */
public enum PrioridadTarea {
    ALTA("Alta"),
    NORMAL("Normal");

    private final String etiqueta;

    PrioridadTarea(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
