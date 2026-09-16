package com.schoolplus.services.tablero;

import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.TableroTarjetaRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Avisa por la campanita de las tarjetas del tablero que ya vencieron (D-025).
 *
 * <p>El tablero es de una sola persona —no hay "asignado a otro" en este modelo (§4.3:
 * "cada usuario tiene el suyo")—, así que el único aviso que tiene sentido es
 * recordarle a la propia persona su tarjeta vencida, no notificar a un tercero.</p>
 *
 * <p>{@code recordatorio_enviado} evita que la misma tarjeta avise todos los días
 * mientras siga vencida: se marca al avisar y solo se limpia si la fecha límite cambia
 * ({@link TableroService#actualizarTarjeta}).</p>
 */
@Component
public class TableroRecordatorioJob {

    private static final Logger log = LoggerFactory.getLogger(TableroRecordatorioJob.class);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TableroTarjetaRepository tarjetaRepository;
    private final NotificacionService notificacionService;

    public TableroRecordatorioJob(TableroTarjetaRepository tarjetaRepository,
                                  NotificacionService notificacionService) {
        this.tarjetaRepository = tarjetaRepository;
        this.notificacionService = notificacionService;
    }

    @Scheduled(cron = "${app.tablero.recordatorio-cron}")
    @Transactional
    public void avisarVencimientos() {
        List<TableroTarjeta> vencidas = tarjetaRepository.pendientesDeRecordatorio(LocalDateTime.now());
        for (TableroTarjeta tarjeta : vencidas) {
            Usuario dueño = tarjeta.getColumna().getTablero().getUsuario();
            notificacionService.crear(dueño.getId(), "TABLERO_VENCIMIENTO",
                    "Tarea vencida: " + tarjeta.getTitulo(),
                    "Vencía el " + tarjeta.getFechaLimite().format(FORMATO) + ".",
                    "/mi-espacio/tablero");
            tarjeta.setRecordatorioEnviado(true);
            tarjetaRepository.save(tarjeta);
        }
        if (!vencidas.isEmpty()) {
            log.info("Recordatorio de tablero: {} tarjeta(s) vencida(s) avisadas.", vencidas.size());
        }
    }
}
