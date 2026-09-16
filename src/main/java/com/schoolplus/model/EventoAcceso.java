package com.schoolplus.model;

/**
 * Eventos que registra la bitácora de accesos (§4.1).
 *
 * <p>Ninguno de los tres repos de referencia tenía bitácora — hallazgo #6 de
 * docs/DECISIONES.md Además de auditar, es la fuente de datos del límite de
 * intentos: contar filas recientes sustituye a un contador en memoria, que no sobreviviría
 * a varias instancias detrás del balanceador (§5.3).</p>
 */
public enum EventoAcceso {
    LOGIN_OK,
    LOGIN_FALLIDO,
    LOGOUT,
    RESET_SOLICITADO,
    RESET_APLICADO,
    REGISTRO_SOLICITADO,
    CUENTA_VERIFICADA,
    BLOQUEO_RATE_LIMIT
}
