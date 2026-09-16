package com.schoolplus.services.documentos;

import java.io.InputStream;

/**
 * Fachada sobre el almacenamiento de archivos. Los servicios dependen de esta interfaz y
 * no de dónde viven los bytes, para poder probarse con un doble en memoria. La
 * implementación real es {@link LocalDocumentoAlmacenamiento} (disco local).
 *
 * <p>La {@code clave} es siempre una ruta relativa ({@code videos/<uuid>.mp4}); la generan
 * {@link PoliticaArchivos} y es lo único que se guarda en la base de datos.</p>
 */
public interface DocumentoAlmacenamiento {

    void subir(String clave, InputStream contenido, long tamanoBytes, String mime);

    InputStream descargar(String clave);

    /**
     * Descarga un rango de bytes (petición {@code Range} HTTP) — para servir video con
     * scrubbing sin traer el archivo entero. {@code offset}/{@code length} en {@code null}
     * equivale a {@link #descargar(String)}.
     */
    InputStream descargar(String clave, Long offset, Long length);

    /** Tamaño y tipo MIME sin descargar el contenido — para armar la respuesta 206 antes de leer bytes. */
    InfoObjeto info(String clave);

    void eliminar(String clave);
}
