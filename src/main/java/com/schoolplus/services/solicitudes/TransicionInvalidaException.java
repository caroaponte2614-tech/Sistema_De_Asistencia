package com.schoolplus.services.solicitudes;

import com.schoolplus.model.EstadoSolicitud;

/**
 * Se intentó una transición que la máquina de estados no admite.
 *
 * <p>Ejemplo típico: aprobar algo que el coordinador ya denegó. El §4.4 dice que ahí el
 * flujo termina, y "termina" significa que ni siquiera Talento Humano puede reabrirlo.</p>
 */
public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(EstadoSolicitud origen, EstadoSolicitud destino) {
        super("No se puede pasar de " + origen + " a " + destino
                + (origen.esFinal() ? ": " + origen + " es un estado final." : "."));
    }

    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
