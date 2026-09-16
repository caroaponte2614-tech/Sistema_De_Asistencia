package com.schoolplus.services.documentos;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Los formatos de archivo que el sistema acepta, con su extensión, su tipo MIME canónico y,
 * cuando existe una fiable, su firma binaria (los primeros bytes del contenido).
 *
 * <p>El MIME que declara el navegador NO se guarda ni se usa para servir: lo decide el
 * cliente y es trivial de falsificar. Se guarda el canónico de esta tabla, y la firma
 * comprueba que un {@code .pdf} empiece de verdad como un PDF.</p>
 *
 * <p>Los contenedores de video (mp4, webm, mov) no llevan firma: sus cabeceras varían
 * según el codificador y una comprobación estricta rechazaría videos legítimos. Al
 * servirse siempre con su MIME canónico y {@code X-Content-Type-Options: nosniff}, un
 * contenido disfrazado no se interpreta como otra cosa.</p>
 */
public enum FormatoArchivo {

    PDF("pdf", "application/pdf", ascii("%PDF-")),
    PNG("png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
    JPG("jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    JPEG("jpeg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    WEBP("webp", "image/webp", ascii("RIFF")),
    DOC("doc", "application/msword", null),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ascii("PK")),
    XLS("xls", "application/vnd.ms-excel", null),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ascii("PK")),
    PPT("ppt", "application/vnd.ms-powerpoint", null),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", ascii("PK")),
    MP4("mp4", "video/mp4", null),
    WEBM("webm", "video/webm", null),
    MOV("mov", "video/quicktime", null);

    /** Bytes que hay que leer del principio del archivo para comprobar cualquier firma. */
    public static final int BYTES_DE_FIRMA = 8;

    private final String extension;
    private final String mime;
    private final byte[] firma;

    FormatoArchivo(String extension, String mime, byte[] firma) {
        this.extension = extension;
        this.mime = mime;
        this.firma = firma;
    }

    public String extension() {
        return extension;
    }

    public String mime() {
        return mime;
    }

    /** ¿El contenido empieza como corresponde a este formato? Sin firma conocida, sí. */
    public boolean coincideFirma(byte[] cabecera) {
        if (firma == null) {
            return true;
        }
        return cabecera != null
                && cabecera.length >= firma.length
                && Arrays.equals(Arrays.copyOf(cabecera, firma.length), firma);
    }

    /** Formato por la extensión de un nombre de archivo o de una clave de almacenamiento. */
    public static Optional<FormatoArchivo> deNombre(String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            return Optional.empty();
        }
        String extension = nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(f -> f.extension.equals(extension))
                .findFirst();
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }
}
