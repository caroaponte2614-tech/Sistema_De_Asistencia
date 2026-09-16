package com.schoolplus.services.documentos;

/** Falló el almacenamiento de archivos (disco, permisos, archivo inexistente o clave inválida). */
public class AlmacenamientoDocumentosException extends RuntimeException {

    public AlmacenamientoDocumentosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
