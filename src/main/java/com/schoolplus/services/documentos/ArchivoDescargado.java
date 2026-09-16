package com.schoolplus.services.documentos;

import java.io.InputStream;

/** Lo que hace falta para servir un archivo de vuelta al navegador. */
public record ArchivoDescargado(InputStream contenido, String nombreArchivo, String mime, long tamanoBytes) {
}
