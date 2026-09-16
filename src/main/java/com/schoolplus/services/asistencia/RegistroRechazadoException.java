package com.schoolplus.services.asistencia;

/**
 * El registro de asistencia no procede, y el motivo es entendible por quien lo intentó.
 *
 * <p>No es un error del sistema: es una regla del módulo diciendo que no. Por eso el
 * controlador REST la traduce a <strong>422</strong> con el texto del motivo, y no a un 500.</p>
 */
public class RegistroRechazadoException extends RuntimeException {

    private final transient MotivoRechazo motivo;

    public RegistroRechazadoException(MotivoRechazo motivo) {
        super(motivo.titulo() + ": " + motivo.mensaje());
        this.motivo = motivo;
    }

    public MotivoRechazo motivo() {
        return motivo;
    }
}
