package com.schoolplus.services.asistencia;

import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.repository.AsistenciaSesionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cierra las sesiones que quedaron abiertas.
 *
 * <p>Pasa: suena el timbre, el profesor cierra el portátil y la sesión sigue viva con su QR
 * rotando. Mientras esté abierta, cualquiera que esté matriculado puede registrarse desde
 * donde sea, y los ausentes de esa clase no existen todavía, así que los reportes la
 * ignoran. A las {@code app.asistencia.duracion-maxima-horas} horas, el sistema la cierra
 * igual que lo habría hecho el profesor: los que faltaron quedan AUSENTE.</p>
 *
 * <p>Un {@code @Scheduled} con el cron en configuración.</p>
 */
@Component
public class CierreAutomaticoJob {

    private static final Logger log = LoggerFactory.getLogger(CierreAutomaticoJob.class);

    private final AsistenciaSesionRepository sesionRepository;
    private final AsistenciaService asistenciaService;
    private final AsistenciaEnVivoHandler enVivo;
    private final ReglasAsistencia reglas;
    private final Clock reloj;

    public CierreAutomaticoJob(AsistenciaSesionRepository sesionRepository,
                               AsistenciaService asistenciaService,
                               AsistenciaEnVivoHandler enVivo,
                               ReglasAsistencia reglas,
                               Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.asistenciaService = asistenciaService;
        this.enVivo = enVivo;
        this.reglas = reglas;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${app.asistencia.cierre-automatico-cron}")
    public void cerrarLasOlvidadas() {
        LocalDateTime limite = LocalDateTime.now(reloj).minusHours(reglas.duracionMaximaHoras());
        List<AsistenciaSesion> olvidadas = sesionRepository.abiertasDesdeAntesDe(limite);
        for (AsistenciaSesion sesion : olvidadas) {
            // Una sesión que falle no puede dejar sin cerrar a las demás.
            try {
                asistenciaService.cerrarPorSistema(sesion.getId());
                enVivo.avisar(sesion.getId());
            } catch (RuntimeException e) {
                log.warn("No se pudo cerrar automáticamente la sesión {}: {}", sesion.getId(), e.getMessage());
            }
        }
        if (!olvidadas.isEmpty()) {
            log.info("Cierre automático: {} sesión(es) de asistencia cerradas por llevar más de {} h abiertas.",
                    olvidadas.size(), reglas.duracionMaximaHoras());
        }
    }
}
