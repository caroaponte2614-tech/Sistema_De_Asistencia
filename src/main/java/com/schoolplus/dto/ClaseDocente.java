package com.schoolplus.dto;

import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.model.Clase;
import com.schoolplus.model.ClaseHorario;

import java.util.List;

/**
 * Una clase tal como la ve su profesor en la pantalla de asistencia: con su horario,
 * cuántos estudiantes tiene y si ya hay una sesión abierta (en cuyo caso el botón no dice
 * "abrir" sino "volver a la proyección").
 */
public record ClaseDocente(Clase clase,
                           List<ClaseHorario> horarios,
                           long inscritos,
                           AsistenciaSesion sesionAbierta) {

    public boolean tieneSesionAbierta() {
        return sesionAbierta != null;
    }
}
