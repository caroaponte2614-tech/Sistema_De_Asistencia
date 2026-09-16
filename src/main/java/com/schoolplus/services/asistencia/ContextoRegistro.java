package com.schoolplus.services.asistencia;

import java.math.BigDecimal;

/**
 * Lo que el servicio necesita saber del intento de registro, además de quién lo hace:
 * de qué celular viene (la cookie sin cifrar; el servicio guarda solo su hash), desde qué
 * IP y agente, y —si la validación por ubicación está encendida— dónde estaba.
 *
 * <p>Lo arma el controlador a partir de la petición HTTP; el servicio no conoce
 * {@code HttpServletRequest} y por eso se puede probar sin web.</p>
 */
public record ContextoRegistro(String identificadorDispositivo,
                               String ip,
                               String userAgent,
                               BigDecimal latitud,
                               BigDecimal longitud) {

    public static ContextoRegistro deDispositivo(String identificadorDispositivo) {
        return new ContextoRegistro(identificadorDispositivo, null, null, null, null);
    }
}
