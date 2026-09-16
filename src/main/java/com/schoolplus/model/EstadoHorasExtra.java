package com.schoolplus.model;

/**
 * Estado de un {@link RegistroHorasExtra}. A diferencia de {@link EstadoSolicitud}, no
 * hace falta una máquina de transiciones: quien registra —el coordinador— nunca es
 * quien decide, así que solo hay un paso, no una cadena.
 */
public enum EstadoHorasExtra {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
