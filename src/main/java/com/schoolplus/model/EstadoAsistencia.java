package com.schoolplus.model;

/**
 * En qué quedó un estudiante en una sesión.
 *
 * <p>PRESENTE y TARDE los pone el sistema al registrarse (la tolerancia de la sesión
 * decide cuál); AUSENTE lo pone el cierre a quien no se registró; JUSTIFICADO solo puede
 * ponerlo el profesor, y exige un motivo escrito.</p>
 */
public enum EstadoAsistencia {

    PRESENTE("Presente"),
    TARDE("Tarde"),
    AUSENTE("Ausente"),
    JUSTIFICADO("Justificado");

    private final String etiqueta;

    EstadoAsistencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** ¿Estuvo en clase? Tarde también cuenta como asistir. */
    public boolean asistio() {
        return this == PRESENTE || this == TARDE;
    }

    /**
     * ¿Cuenta para el porcentaje? Una falta justificada no suma ni resta: sale del
     * denominador (ver {@code ResumenAsistencia}).
     */
    public boolean cuentaEnElPorcentaje() {
        return this != JUSTIFICADO;
    }
}
