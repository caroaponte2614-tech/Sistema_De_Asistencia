package com.schoolplus.model;

/**
 * Tipo de {@link Curso}. La inducción no es un módulo aparte: es un curso con
 * {@code tipo = INDUCCION} (normalmente además {@code obligatorio = true}), que usa el
 * mismo motor de video + quiz que cualquier capacitación.
 */
public enum TipoCurso {
    CAPACITACION,
    INDUCCION
}
