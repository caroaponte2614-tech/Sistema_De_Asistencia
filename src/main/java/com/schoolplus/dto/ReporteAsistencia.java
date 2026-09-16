package com.schoolplus.dto;

import com.schoolplus.model.AsistenciaRegistro;

import java.util.List;

/**
 * El reporte completo: los mismos registros agrupados de tres maneras, más el detalle.
 *
 * <p>Se calcula una sola vez y la pantalla y la exportación leen de aquí: si el Excel y la
 * tabla salieran de consultas distintas, un día dirían cosas distintas.</p>
 */
public record ReporteAsistencia(FiltroAsistencia filtro,
                                ResumenAsistencia total,
                                List<ResumenAsistencia> porEstudiante,
                                List<ResumenAsistencia> porClase,
                                List<ResumenSesion> porSesion,
                                List<AsistenciaRegistro> detalle) {

    public boolean vacio() {
        return detalle.isEmpty();
    }
}
