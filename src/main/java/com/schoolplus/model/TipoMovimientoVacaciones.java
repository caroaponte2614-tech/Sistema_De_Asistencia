package com.schoolplus.model;

/**
 * Por qué se movió el saldo de vacaciones.
 *
 * <p>RESERVA y CONSUMO están separados a propósito: entre que alguien pide vacaciones y
 * que RRHH las valida pasan días, y en ese hueco los días no están tomados pero tampoco
 * disponibles. Sin esa distinción, dos solicitudes que caben por separado pero no juntas
 * pasarían las dos.</p>
 */
public enum TipoMovimientoVacaciones {
    /** Los días que va generando el tiempo trabajado. */
    CAUSACION,
    /** Se apartan al enviar la solicitud. */
    RESERVA,
    /** La reserva se convierte en días tomados al aprobarse. */
    CONSUMO,
    /** La reserva se devuelve porque denegaron. */
    REVERSA,
    /** Corrección manual de Talento Humano. */
    AJUSTE
}
