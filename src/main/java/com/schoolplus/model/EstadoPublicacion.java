package com.schoolplus.model;

/**
 * Moderación (§4.7). Se OCULTA, no se borra: borrar deja el hilo de comentarios huérfano
 * y sin explicación, y elimina la prueba de qué se moderó.
 */
public enum EstadoPublicacion {
    VISIBLE,
    OCULTA
}
