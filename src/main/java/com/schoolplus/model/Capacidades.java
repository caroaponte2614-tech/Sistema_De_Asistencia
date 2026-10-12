package com.schoolplus.model;

/**
 * Códigos de la tabla {@code capacidad}, con nombre.
 *
 * <p>La matriz vive en la base de datos —eso no cambia—, pero los códigos se escriben
 * en {@code @PreAuthorize} y en las plantillas, y ahí una constante evita que un dedazo
 * se convierta en un permiso que nunca concede nada y en una pantalla en blanco sin
 * explicación. {@code CapacidadesTest} comprueba que esta lista y las migraciones no se
 * separen.</p>
 */
public final class Capacidades {

    /** Alta, edición, activación y roles de los usuarios. */
    public static final String USUARIO_ADMINISTRAR = "USUARIO_ADMINISTRAR";

    // ── Asistencia (V25) ─────────────────────────────────────────────────────
    /** Crear clases, horarios y matricular estudiantes. */
    public static final String CLASE_ADMINISTRAR = "CLASE_ADMINISTRAR";
    /** Abrir y cerrar sesiones, corregir registros y autorizar celulares. Ámbito PROPIO = mis clases. */
    public static final String ASISTENCIA_TOMAR = "ASISTENCIA_TOMAR";
    /** Registrar la propia asistencia con QR o con el código de 6 dígitos. */
    public static final String ASISTENCIA_REGISTRAR = "ASISTENCIA_REGISTRAR";
    /** Ver y exportar reportes de asistencia. */
    public static final String ASISTENCIA_REPORTE = "ASISTENCIA_REPORTE";

    private Capacidades() {
    }
}
