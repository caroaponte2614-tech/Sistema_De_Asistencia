package com.schoolplus.services.documentos;

/** Lo que hace falta saber de un objeto en el almacenamiento antes de leer su contenido. */
public record InfoObjeto(long tamanoBytes, String mime) {
}
