package com.schoolplus.model;

/**
 * Ciclo de vida del celular vinculado a un estudiante.
 *
 * <p>El estudiante no elige: la primera vez que registra asistencia, su teléfono queda
 * ACTIVO solo. A partir de ahí, otro teléfono entra como PENDIENTE y necesita que un
 * profesor o un administrador lo apruebe — es lo que impide que dos personas se registren
 * turnándose el mismo celular, o que alguien registre desde el de un compañero.</p>
 */
public enum EstadoDispositivo {

    /** Cambio de celular pedido, a la espera de que un profesor o un admin lo apruebe. */
    PENDIENTE("Pendiente de aprobación"),

    /** El celular con el que ese estudiante registra asistencia hoy. */
    ACTIVO("Activo"),

    /** Era el activo y otro lo reemplazó al aprobarse un cambio. */
    REEMPLAZADO("Reemplazado"),

    /** Un profesor o un admin negó el cambio. */
    RECHAZADO("Rechazado"),

    /** Se desvinculó a propósito: el estudiante vuelve a empezar con el que use. */
    REVOCADO("Revocado");

    private final String etiqueta;

    EstadoDispositivo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
