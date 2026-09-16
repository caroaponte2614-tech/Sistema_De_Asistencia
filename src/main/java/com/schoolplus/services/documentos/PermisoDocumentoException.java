package com.schoolplus.services.documentos;

/** El actor no tiene, sobre este documento puntual, la capacidad que la acción exige. */
public class PermisoDocumentoException extends RuntimeException {

    public PermisoDocumentoException(String mensaje) {
        super(mensaje);
    }
}
