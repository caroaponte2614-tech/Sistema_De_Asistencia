package com.schoolplus.services.solicitudes;

/**
 * Quien intenta decidir no es quien debe.
 *
 * <p>Cubre los dos casos que el §4.4 marca: aprobar una solicitud de otra área, y ser el
 * propio solicitante ("un solicitante no puede ser su propio aprobador").</p>
 */
public class NoAutorizadoADecidirException extends RuntimeException {

    public NoAutorizadoADecidirException(String mensaje) {
        super(mensaje);
    }
}
