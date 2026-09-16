package com.schoolplus.services.organigrama;

/**
 * Asignar este jefe cerraría un ciclo: alguien terminaría reportando, directa o
 * indirectamente, a su propio subordinado.
 */
public class CicloDeOrganigramaException extends RuntimeException {

    public CicloDeOrganigramaException(String mensaje) {
        super(mensaje);
    }
}
