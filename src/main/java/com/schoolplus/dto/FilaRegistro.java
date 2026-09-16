package com.schoolplus.dto;

/**
 * Una línea de la lista de asistencia en vivo. Lleva el estado dos veces —el nombre del
 * enum para que el JavaScript elija el color, y la etiqueta para mostrarla— y así la
 * vista no tiene que traducir nada.
 */
public record FilaRegistro(String registroId,
                           String estudianteId,
                           String estudiante,
                           String hora,
                           String estado,
                           String estadoEtiqueta,
                           String metodo,
                           boolean corregido,
                           String observacion) {
}
