package com.schoolplus.model;

/**
 * Qué es una entrada del muro (§4.7).
 *
 * <p>Las tres comparten tabla porque las tres son lo mismo desde el feed: algo con autor,
 * fecha, comentarios y reacciones. Separarlas en tres tablas obligaría a un UNION en cada
 * página del muro.</p>
 */
public enum TipoPublicacion {
    POST,
    ENCUESTA,
    RECONOCIMIENTO
}
