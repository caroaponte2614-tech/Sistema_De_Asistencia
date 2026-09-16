package com.schoolplus.dto;

import com.schoolplus.model.Solicitud;

import java.util.List;

/**
 * Una fila de la bandeja de aprobación.
 *
 * @param solicitud lo que hay que decidir.
 * @param cruces    solicitudes del mismo equipo que se solapan en fechas. Es una
 *                  <em>advertencia</em>, no un bloqueo (§4.5): el aprobador decide.
 */
public record SolicitudPendiente(Solicitud solicitud, List<Solicitud> cruces) {
}
