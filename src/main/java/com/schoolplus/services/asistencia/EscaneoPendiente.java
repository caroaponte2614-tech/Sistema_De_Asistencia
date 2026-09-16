package com.schoolplus.services.asistencia;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Un QR ya comprobado que espera en la sesión HTTP a que la persona termine de entrar.
 *
 * <p>Es el puente del camino "cámara nativa del teléfono": el QR abre un enlace, el
 * servidor comprueba el token en ese mismo instante —cuando todavía está vigente— y, si
 * quien lo abrió no tenía sesión iniciada, guarda aquí el resultado mientras se loguea. Sin
 * esto, el token habría vencido para cuando termina de escribir su contraseña.</p>
 *
 * <p>El escaneo caduca a los {@link ReglasAsistencia#segundosParaConfirmar()} segundos, así
 * que la espera no es una puerta abierta: el QR tuvo que estar vigente al escanearlo.</p>
 *
 * <p>{@link Serializable} porque la sesión vive en la base de datos (Spring Session JDBC) y
 * se serializa para guardarse.</p>
 */
public record EscaneoPendiente(String sesionId, LocalDateTime escaneadoEn) implements Serializable {

    /** Clave con la que viaja en la sesión HTTP. */
    public static final String ATRIBUTO = "asistenciaEscaneoPendiente";
}
