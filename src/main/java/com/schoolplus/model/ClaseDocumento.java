package com.schoolplus.model;

/**
 * Clase de una {@link DocumentoCategoria} (§4.2).
 *
 * <p>RESTRINGIDO = de expediente (contrato, afiliaciones...). Solo lo sube, edita o
 * borra quien tenga {@code DOCUMENTO_RESTRINGIDO_SUBIR} (ADMIN y, desde D-017, RH). El
 * dueño únicamente lo lee.</p>
 *
 * <p>PUBLICO = del propio colaborador (hoja de vida, certificados, soportes). Cada quien
 * sube los suyos: es su "carpeta" personal.</p>
 */
public enum ClaseDocumento {
    RESTRINGIDO,
    PUBLICO
}
