package com.schoolplus.services.solicitudes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cuenta los días hábiles de un rango.
 *
 * <p>De aquí sale el número que se descuenta del saldo de vacaciones, así que un error de
 * un día aquí es un día de vacaciones regalado o robado a cada persona.</p>
 *
 * <h2>Dos límites que conviene tener presentes</h2>
 * <ol>
 *   <li><strong>Qué días cuentan es configuración, no código.</strong> Por defecto de
 *       lunes a viernes. Si en la institución el sábado es hábil para efectos de vacaciones,
 *       se cambia {@code app.vacaciones.dias-habiles} sin tocar nada más. Se eligió
 *       lunes-viernes porque es lo más común y porque el prompt no lo especifica.</li>
 *   <li><strong>Los festivos NO se excluyen todavía.</strong> No existe calendario de
 *       festivos en ningún repo de referencia y los de Colombia se mueven con la ley
 *       Emiliani, así que inventarlos habría sido peor que no tenerlos. Mientras no haya
 *       tabla de festivos, un rango que abarque un lunes festivo cuenta ese día como
 *       hábil y descuenta de más. Está anotado como pregunta abierta.</li>
 * </ol>
 */
@Service
public class DiasHabilesService {

    private final Set<DayOfWeek> diasHabiles;

    public DiasHabilesService(
            @Value("${app.vacaciones.dias-habiles:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}")
            String configurados) {
        this.diasHabiles = Arrays.stream(configurados.split(","))
                .map(String::trim)
                .filter(dia -> !dia.isEmpty())
                .map(dia -> DayOfWeek.valueOf(dia.toUpperCase()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));

        if (diasHabiles.isEmpty()) {
            throw new IllegalStateException(
                    "app.vacaciones.dias-habiles no puede quedar vacío: ninguna solicitud contaría días.");
        }
    }

    /** Días hábiles entre las dos fechas, ambas incluidas. */
    public BigDecimal contar(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null || fin.isBefore(inicio)) {
            return BigDecimal.ZERO;
        }
        int total = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
            if (esHabil(dia)) {
                total++;
            }
        }
        return BigDecimal.valueOf(total);
    }

    public boolean esHabil(LocalDate dia) {
        return diasHabiles.contains(dia.getDayOfWeek());
    }

    public Set<DayOfWeek> getDiasHabiles() {
        return Set.copyOf(diasHabiles);
    }
}
