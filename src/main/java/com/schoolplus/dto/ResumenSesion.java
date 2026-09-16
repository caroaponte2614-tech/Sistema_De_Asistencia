package com.schoolplus.dto;

/** Una fila del reporte por sesión: qué clase, cuándo y cómo quedó el grupo. */
public record ResumenSesion(String sesionId,
                            String clase,
                            String profesor,
                            String fecha,
                            String hora,
                            ResumenAsistencia conteo) {
}
