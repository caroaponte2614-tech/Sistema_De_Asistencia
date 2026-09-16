package com.schoolplus.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los cuatro contadores de un estudiante, una clase o una sesión, con su porcentaje.
 *
 * <h2>Cómo se calcula el porcentaje</h2>
 * {@code (presentes + tardes) / (total − justificados)}. Una falta justificada no suma ni
 * resta: sale del denominador. Llegar tarde sí cuenta como asistir — el dato de la
 * puntualidad está en su propia columna.
 *
 * <p>Sin sesiones contables el porcentaje es {@code null} y la vista pinta un guion, que es
 * más honesto que un 0% o un 100% inventados.</p>
 */
public record ResumenAsistencia(String id,
                                String etiqueta,
                                String detalle,
                                long presentes,
                                long tardes,
                                long ausentes,
                                long justificados) {

    public long total() {
        return presentes + tardes + ausentes + justificados;
    }

    /** Las sesiones que entran en el porcentaje (todas menos las justificadas). */
    public long contabilizadas() {
        return presentes + tardes + ausentes;
    }

    public BigDecimal porcentaje() {
        long base = contabilizadas();
        if (base == 0) {
            return null;
        }
        return BigDecimal.valueOf(presentes + tardes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
    }

    /** "87,5 %" o "—". Para pintarlo sin lógica en la plantilla. */
    public String porcentajeLegible() {
        BigDecimal porcentaje = porcentaje();
        return porcentaje == null ? "—" : porcentaje.toPlainString().replace('.', ',') + " %";
    }
}
