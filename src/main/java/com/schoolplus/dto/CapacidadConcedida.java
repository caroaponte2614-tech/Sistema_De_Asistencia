package com.schoolplus.dto;

import com.schoolplus.model.Ambito;

/** Una capacidad que la matriz de permisos concede al usuario, con su alcance. */
public record CapacidadConcedida(String codigo, String descripcion, String modulo, Ambito ambito) {
}
