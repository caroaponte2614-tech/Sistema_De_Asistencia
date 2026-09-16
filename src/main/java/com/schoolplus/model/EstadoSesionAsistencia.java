package com.schoolplus.model;

/**
 * Estado de una sesión de asistencia. Solo dos, y el paso entre ellos es de ida:
 * una sesión cerrada no se reabre — corregir lo que quedó mal es cambiar registros,
 * no volver a repartir QR.
 */
public enum EstadoSesionAsistencia {

    /** El QR rota, el código sirve y los estudiantes pueden registrarse. */
    ABIERTA,

    /** Ya no se admiten registros nuevos; los inscritos que faltaron quedaron AUSENTE. */
    CERRADA
}
