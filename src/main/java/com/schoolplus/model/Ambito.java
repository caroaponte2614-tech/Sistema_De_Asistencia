package com.schoolplus.model;

/**
 * Sobre quién aplica una capacidad.
 *
 * <p>La matriz del §3.1 del prompt mezcla la capacidad con su alcance ("abrir la asistencia
 * de <em>mis</em> clases", "ver los reportes de <em>todas</em>"). Sin este eje, la mitad de
 * las reglas volvería a caer en {@code if}s dentro del código, que es justo lo que el
 * prompt prohíbe.</p>
 */
public enum Ambito {

    /** Solo sobre sus propios datos. */
    PROPIO,

    /** Sobre toda la institución. */
    TODOS
}
