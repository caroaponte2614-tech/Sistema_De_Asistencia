package com.schoolplus.model;

/**
 * Sobre quién aplica una capacidad.
 *
 * <p>La matriz del §3.1 del prompt mezcla la capacidad con su alcance ("ver <em>todos</em>
 * los tableros", "reportes <em>solo de su área</em>"). Sin este eje, la mitad de las reglas
 * volvería a caer en {@code if}s dentro del código, que es justo lo que el prompt prohíbe.</p>
 */
public enum Ambito {

    /** Solo sobre sus propios datos. */
    PROPIO,

    /** Sobre la gente del área que coordina. */
    AREA,

    /** Sobre toda la compañía. */
    TODOS
}
