package com.schoolplus.services.auth;

import java.util.List;

/**
 * Salida del auto-registro.
 *
 * @param errores          vacío si la solicitud quedó guardada.
 * @param enlaceDesarrollo enlace de verificación, y solo cuando el envío de correo está
 *                         apagado ({@code app.mail.enabled=false}). Sirve para poder
 *                         probar el flujo completo sin cuenta SMTP; en producción es
 *                         siempre {@code null}.
 */
public record ResultadoRegistro(List<String> errores, String enlaceDesarrollo) {

    public boolean exitoso() {
        return errores.isEmpty();
    }

    public static ResultadoRegistro conErrores(List<String> errores) {
        return new ResultadoRegistro(errores, null);
    }
}
