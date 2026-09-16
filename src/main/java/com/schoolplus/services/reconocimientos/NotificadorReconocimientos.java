package com.schoolplus.services.reconocimientos;

import com.schoolplus.model.RegistroReconocimiento;
import com.schoolplus.model.TipoReconocimiento;
import com.schoolplus.services.email.EmailService;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Avisa al colaborador cuando le registran un reconocimiento o un llamado de atención.
 * Corre después del commit de {@code ReconocimientoService.registrar}, mismo motivo que
 * {@code NotificadorHorasExtra}: un correo lento no debe bloquear a quien registra.
 */
@Service
public class NotificadorReconocimientos {

    private static final Logger log = LoggerFactory.getLogger(NotificadorReconocimientos.class);
    private static final String RUTA_MIS_RECONOCIMIENTOS = "/mi-espacio/reconocimientos";

    private final EmailService emailService;
    private final NotificacionService notificacionService;

    public NotificadorReconocimientos(EmailService emailService, NotificacionService notificacionService) {
        this.emailService = emailService;
        this.notificacionService = notificacionService;
    }

    @Async
    public void avisarNuevoRegistro(RegistroReconocimiento registro) {
        boolean esReconocimiento = registro.getTipo() == TipoReconocimiento.RECONOCIMIENTO;
        String titulo = esReconocimiento ? "¡Tienes un reconocimiento nuevo!" : "Tienes un llamado de atención nuevo";
        String cuerpo = registro.getTitulo() + " — " + registro.getDescripcion();

        try {
            emailService.enviarAvisoSolicitud(
                    registro.getColaborador().getEmail(), registro.getColaborador().getNombre(),
                    titulo + " — School_Plus", esReconocimiento ? "Reconocimiento" : "Llamado de atención", cuerpo);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar a {}: {}", registro.getColaborador().getEmail(), e.getMessage(), e);
        }
        notificacionService.crear(registro.getColaborador().getId(),
                esReconocimiento ? "RECONOCIMIENTO_NUEVO" : "LLAMADO_ATENCION_NUEVO",
                titulo, cuerpo, RUTA_MIS_RECONOCIMIENTOS);
    }
}
