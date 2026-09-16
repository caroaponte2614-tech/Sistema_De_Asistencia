package com.schoolplus.model;

import java.util.Set;

/**
 * Los seis estados del diagrama del §4.4. Ni uno más.
 *
 * <p>Cada estado sabe a cuáles puede pasar, y esa es toda la máquina: el servicio no
 * decide transiciones con {@code if}s encadenados, pregunta aquí. Un estado final
 * declara un conjunto vacío, así que "el flujo termina ahí" deja de ser un comentario
 * y pasa a ser código que se puede probar.</p>
 */
public enum EstadoSolicitud {

    /** La escribió el solicitante pero todavía no la envió. */
    BORRADOR,

    /** En la bandeja del coordinador del área. Primera instancia. */
    PENDIENTE_COORDINADOR,

    /** En la bandeja de Talento Humano. Segunda y última instancia. */
    PENDIENTE_SUPERVISOR,

    /** Aprobada y validada. FINAL. */
    APROBADO,

    /** El coordinador denegó. FINAL: nunca llega a RRHH (§4.4). */
    RECHAZADO_COORD,

    /** Talento Humano denegó. FINAL: el permiso no queda validado (§4.4). */
    RECHAZADO_RRHH;

    static {
        BORRADOR.siguientes = Set.of(PENDIENTE_COORDINADOR, PENDIENTE_SUPERVISOR);
        PENDIENTE_COORDINADOR.siguientes = Set.of(PENDIENTE_SUPERVISOR, RECHAZADO_COORD);
        PENDIENTE_SUPERVISOR.siguientes = Set.of(APROBADO, RECHAZADO_RRHH);
        APROBADO.siguientes = Set.of();
        RECHAZADO_COORD.siguientes = Set.of();
        RECHAZADO_RRHH.siguientes = Set.of();
    }

    private Set<EstadoSolicitud> siguientes;

    public boolean puedePasarA(EstadoSolicitud destino) {
        return siguientes.contains(destino);
    }

    public Set<EstadoSolicitud> siguientes() {
        return siguientes;
    }

    /** Un estado final no admite ninguna transición más. */
    public boolean esFinal() {
        return siguientes.isEmpty();
    }

    /** ¿Está esperando la decisión de alguien? */
    public boolean estaPendiente() {
        return this == PENDIENTE_COORDINADOR || this == PENDIENTE_SUPERVISOR;
    }
}
