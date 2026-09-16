package com.schoolplus.services.organigrama;

import com.schoolplus.model.Usuario;

/**
 * Un colaborador cuyo jefe directo actual ({@code colaborador.getCoordinador()}) no
 * coincide con lo que sugeriría {@code jefe_cargo} para su cargo — sea porque RH lo
 * reasignó a mano, o porque le quitó el jefe sugerido sin poner otro.
 * {@code jefeSugerido} es {@code null} cuando el cargo del colaborador no tiene ningún
 * jefe asignado en {@code jefe_cargo}.
 */
public record ExcepcionManual(Usuario colaborador, Usuario jefeSugerido) {
}
