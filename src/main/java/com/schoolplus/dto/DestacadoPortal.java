package com.schoolplus.dto;

import com.schoolplus.model.Curso;
import com.schoolplus.model.DocumentoInstitucional;

import java.time.LocalDateTime;

/**
 * Una tarjeta del carrusel del portal: un curso o un documento institucional (políticas,
 * manuales, reglamento de trabajo, bienestar/burnout) en el mismo formato, para poder
 * mezclarlos en un solo {@code th:each} sin que la plantilla conozca la diferencia.
 */
public record DestacadoPortal(String tipo, String etiqueta, String titulo, String resumen,
                              String miniaturaUrl, String href, LocalDateTime creadoEn) {

    public static DestacadoPortal deCurso(Curso curso) {
        return new DestacadoPortal(
                "CURSO",
                curso.getTipo().name().equals("INDUCCION") ? "Inducción" : "Capacitación",
                curso.getTitulo(),
                curso.getDescripcion(),
                curso.getMiniaturaClave() == null
                        ? null : "/mi-espacio/capacitaciones/" + curso.getId() + "/miniatura",
                "/mi-espacio/capacitaciones/" + curso.getId(),
                curso.getCreadoEn());
    }

    public static DestacadoPortal deDocumento(DocumentoInstitucional documento) {
        String categoria = documento.getCategoria();
        return new DestacadoPortal(
                "DOCUMENTO",
                categoria == null || categoria.isBlank() ? "Documento" : categoria,
                documento.getTitulo(),
                documento.getDescripcion(),
                documento.getMiniaturaClave() == null
                        ? null : "/mi-espacio/documentos-institucionales/" + documento.getId() + "/miniatura",
                "/mi-espacio/documentos-institucionales/" + documento.getId() + "/ver",
                documento.getCreadoEn());
    }
}
