package com.schoolplus.services.documentos;

import com.schoolplus.util.UuidV7;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * El único sitio que decide si un archivo subido se acepta y con qué clave se guarda.
 *
 * <p>Antes cada servicio armaba su clave concatenando el nombre original del archivo
 * ({@code "cursos/" + id + "/video-" + nombre}), así que un nombre con {@code ../} o con
 * caracteres raros acababa en la ruta. Ahora la clave es siempre
 * {@code <carpeta del tipo>/<uuid>.<extensión admitida>}: el nombre que puso el usuario
 * nunca toca el disco, solo se guarda aparte para mostrarlo.</p>
 *
 * <p>Los errores son {@link IllegalArgumentException} con un mensaje para la persona, igual
 * que el resto de validaciones de los servicios: los controladores ya los pintan.</p>
 */
public final class PoliticaArchivos {

    private static final int LARGO_MAXIMO_NOMBRE = 255;

    private PoliticaArchivos() {
    }

    /**
     * Valida tamaño, extensión y firma del contenido, y devuelve la clave nueva.
     *
     * @throws IllegalArgumentException si el archivo no cumple la política de su tipo
     */
    public static ArchivoValidado validar(MultipartFile archivo, TipoArchivo tipo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Elige un archivo antes de subirlo.");
        }
        if (archivo.getSize() > tipo.tamanoMaximoBytes()) {
            throw new IllegalArgumentException(
                    "El archivo supera el tamaño máximo de " + tipo.tamanoMaximoLegible() + ".");
        }
        FormatoArchivo formato = FormatoArchivo.deNombre(archivo.getOriginalFilename())
                .filter(tipo::admite)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo de archivo no permitido. Se admite: " + tipo.extensionesLegibles() + "."));
        if (!formato.coincideFirma(cabecera(archivo))) {
            throw new IllegalArgumentException(
                    "El contenido del archivo no corresponde a un ." + formato.extension() + ".");
        }
        return new ArchivoValidado(nuevaClave(tipo, formato), formato);
    }

    /** Clave única para un archivo que genera el propio sistema (no pasa por validación). */
    public static String nuevaClave(TipoArchivo tipo, FormatoArchivo formato) {
        return tipo.carpeta() + "/" + UuidV7.nuevoId() + "." + formato.extension();
    }

    /**
     * El nombre original, solo para mostrarlo: sin carpetas (los navegadores viejos mandan
     * la ruta completa) y recortado al largo de la columna.
     */
    public static String nombreParaMostrar(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "archivo";
        }
        String nombre = nombreOriginal.substring(
                Math.max(nombreOriginal.lastIndexOf('/'), nombreOriginal.lastIndexOf('\\')) + 1).strip();
        if (nombre.isEmpty()) {
            return "archivo";
        }
        return nombre.length() > LARGO_MAXIMO_NOMBRE ? nombre.substring(0, LARGO_MAXIMO_NOMBRE) : nombre;
    }

    private static byte[] cabecera(MultipartFile archivo) {
        try (InputStream flujo = archivo.getInputStream()) {
            return flujo.readNBytes(FormatoArchivo.BYTES_DE_FIRMA);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo enviado.", e);
        }
    }

    /** Resultado de una validación correcta: la clave donde guardarlo y su formato real. */
    public record ArchivoValidado(String clave, FormatoArchivo formato) {

        /** MIME canónico del formato, no el que declaró el navegador. */
        public String mime() {
            return formato.mime();
        }
    }
}
