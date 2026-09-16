package com.schoolplus.services.documentos;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.schoolplus.services.documentos.FormatoArchivo.DOC;
import static com.schoolplus.services.documentos.FormatoArchivo.DOCX;
import static com.schoolplus.services.documentos.FormatoArchivo.JPEG;
import static com.schoolplus.services.documentos.FormatoArchivo.JPG;
import static com.schoolplus.services.documentos.FormatoArchivo.MOV;
import static com.schoolplus.services.documentos.FormatoArchivo.MP4;
import static com.schoolplus.services.documentos.FormatoArchivo.PDF;
import static com.schoolplus.services.documentos.FormatoArchivo.PNG;
import static com.schoolplus.services.documentos.FormatoArchivo.PPT;
import static com.schoolplus.services.documentos.FormatoArchivo.PPTX;
import static com.schoolplus.services.documentos.FormatoArchivo.WEBM;
import static com.schoolplus.services.documentos.FormatoArchivo.WEBP;
import static com.schoolplus.services.documentos.FormatoArchivo.XLS;
import static com.schoolplus.services.documentos.FormatoArchivo.XLSX;

/**
 * Qué clase de archivo se está guardando: decide la subcarpeta del almacenamiento, los
 * formatos admitidos y el tamaño máximo. Es la única tabla que hay que tocar para admitir
 * un formato nuevo en un módulo.
 *
 * <p>El tope por tipo va además del límite global de {@code spring.servlet.multipart}: ese
 * corta la petición entera, este da un mensaje entendible por clase de archivo.</p>
 */
public enum TipoArchivo {

    /** Expediente del usuario: hoja de vida, certificados, soportes personales. */
    DOCUMENTO("documentos", megas(20), PDF, PNG, JPG, JPEG, WEBP, DOC, DOCX, XLS, XLSX),
    /** Adjunto de una solicitud o de un reconocimiento. */
    SOPORTE("soportes", megas(10), PDF, PNG, JPG, JPEG, WEBP, DOC, DOCX),
    /** Manuales, políticas y formatos que ve toda la institución. */
    INSTITUCIONAL("institucionales", megas(50), PDF, PNG, JPG, JPEG, WEBP, DOC, DOCX, XLS, XLSX, PPT, PPTX),
    /** Video de una capacitación. */
    VIDEO("videos", megas(300), MP4, WEBM, MOV),
    /** Miniatura de una tarjeta del catálogo. */
    IMAGEN("imagenes", megas(5), PNG, JPG, JPEG, WEBP),
    /** PDF que genera el propio sistema (certificados). */
    CERTIFICADO("certificados", megas(5), PDF);

    private final String carpeta;
    private final long tamanoMaximoBytes;
    private final Set<FormatoArchivo> formatos;

    TipoArchivo(String carpeta, long tamanoMaximoBytes, FormatoArchivo... formatos) {
        this.carpeta = carpeta;
        this.tamanoMaximoBytes = tamanoMaximoBytes;
        this.formatos = EnumSet.copyOf(Set.of(formatos));
    }

    public String carpeta() {
        return carpeta;
    }

    public long tamanoMaximoBytes() {
        return tamanoMaximoBytes;
    }

    public boolean admite(FormatoArchivo formato) {
        return formatos.contains(formato);
    }

    /** "pdf, png, jpg" — para el mensaje de error. */
    public String extensionesLegibles() {
        return formatos.stream().map(FormatoArchivo::extension).collect(Collectors.joining(", "));
    }

    public String tamanoMaximoLegible() {
        return (tamanoMaximoBytes / (1024 * 1024)) + " MB";
    }

    private static long megas(long cantidad) {
        return cantidad * 1024 * 1024;
    }
}
