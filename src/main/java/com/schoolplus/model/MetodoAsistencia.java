package com.schoolplus.model;

/**
 * Por dónde entró un registro de asistencia. Queda guardado para la auditoría: si alguien
 * revisa un curso entero de registros MANUAL, esa es justamente la conversación que hay
 * que tener.
 */
public enum MetodoAsistencia {

    /** El estudiante escaneó el QR (lector de la app o cámara nativa del teléfono). */
    QR("QR"),

    /** El estudiante escribió el código de 6 dígitos que rota en la pantalla. */
    CODIGO("Código"),

    /** Lo marcó el profesor a mano: quien no tiene celular, o una corrección posterior. */
    MANUAL("Manual"),

    /** Ausente generado por el sistema al cerrar la sesión. */
    CIERRE("Cierre");

    private final String etiqueta;

    MetodoAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
