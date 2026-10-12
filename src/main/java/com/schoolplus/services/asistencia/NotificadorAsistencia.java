package com.schoolplus.services.asistencia;

import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.springframework.stereotype.Component;

/**
 * Los avisos de la campanita que genera la vinculación de celulares.
 *
 * <p>El estudiante que cambia de teléfono queda bloqueado hasta que un profesor suyo lo
 * autorice; sin aviso, la solicitud espera en una lista que nadie está mirando. Por eso
 * se avisa a sus profesores al pedirlo, y a él cuando alguien decide.</p>
 *
 * <p>Corre dentro de la transacción de quien llama: si la operación se deshace, el aviso
 * guardado también.</p>
 */
@Component
public class NotificadorAsistencia {

    static final String TIPO_CELULAR_PEDIDO = "ASISTENCIA_CELULAR_PEDIDO";
    static final String TIPO_CELULAR_APROBADO = "ASISTENCIA_CELULAR_APROBADO";
    static final String TIPO_CELULAR_RECHAZADO = "ASISTENCIA_CELULAR_RECHAZADO";
    static final String TIPO_CELULAR_REVOCADO = "ASISTENCIA_CELULAR_REVOCADO";

    /** Donde el docente ve y resuelve las solicitudes pendientes. */
    private static final String ENLACE_DOCENTE = "/docencia/asistencia";
    private static final String ENLACE_ESTUDIANTE = "/estudiante/asistencia";

    private final NotificacionService notificaciones;
    private final ClaseInscripcionRepository inscripcionRepository;

    public NotificadorAsistencia(NotificacionService notificaciones,
                                 ClaseInscripcionRepository inscripcionRepository) {
        this.notificaciones = notificaciones;
        this.inscripcionRepository = inscripcionRepository;
    }

    /** A cada profesor del estudiante, una vez aunque le dicte varias clases. */
    public void cambioDeCelularPedido(Usuario estudiante, AsistenciaDispositivo solicitud) {
        String cuerpo = estudiante.getNombre() + " quiere registrar su asistencia desde otro celular ("
                + solicitud.getDescripcion() + "). Hasta que lo autorices no puede registrarse.";
        for (String profesorId : inscripcionRepository.profesoresDe(estudiante.getId())) {
            notificaciones.crear(profesorId, TIPO_CELULAR_PEDIDO,
                    "Cambio de celular por autorizar", cuerpo, ENLACE_DOCENTE);
        }
    }

    public void cambioDeCelularAprobado(AsistenciaDispositivo solicitud) {
        notificaciones.crear(solicitud.getEstudiante().getId(), TIPO_CELULAR_APROBADO,
                "Tu nuevo celular quedó autorizado",
                "Ya puedes registrar tu asistencia desde " + solicitud.getDescripcion() + ".",
                ENLACE_ESTUDIANTE);
    }

    public void cambioDeCelularRechazado(AsistenciaDispositivo solicitud) {
        notificaciones.crear(solicitud.getEstudiante().getId(), TIPO_CELULAR_RECHAZADO,
                "No se autorizó el cambio de celular",
                "Sigue registrando tu asistencia desde el celular que ya tenías vinculado. "
                        + "Si lo perdiste, habla con tu profesor.",
                ENLACE_ESTUDIANTE);
    }

    public void celularDesvinculado(AsistenciaDispositivo dispositivo) {
        notificaciones.crear(dispositivo.getEstudiante().getId(), TIPO_CELULAR_REVOCADO,
                "Tu celular fue desvinculado",
                "El próximo celular desde el que registres tu asistencia quedará vinculado a tu cuenta.",
                ENLACE_ESTUDIANTE);
    }
}
