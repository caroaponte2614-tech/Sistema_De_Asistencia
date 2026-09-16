package com.schoolplus.dto;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Qué se quiere mirar en el reporte: una clase, un estudiante, un rango de fechas, o la
 * combinación que sea. Los campos vacíos significan "todo".
 *
 * <p>El rango es inclusivo en los dos extremos; el servicio lo traduce a
 * {@code [desde 00:00, hasta+1día 00:00)} al consultar.</p>
 */
public record FiltroAsistencia(String claseId, String estudianteId, LocalDate desde, LocalDate hasta) {

    private static final int DIAS_POR_DEFECTO = 30;

    /** Completa lo que no vino: por defecto, el último mes hasta hoy. */
    public FiltroAsistencia conValoresPorDefecto(Clock reloj) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fin = hasta == null ? hoy : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(DIAS_POR_DEFECTO) : desde;
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
        return new FiltroAsistencia(vacioANulo(claseId), vacioANulo(estudianteId), inicio, fin);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
