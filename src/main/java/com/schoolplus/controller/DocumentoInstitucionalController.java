package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.DocumentoInstitucional;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentosinstitucionales.DocumentoInstitucionalReporteService;
import com.schoolplus.services.documentosinstitucionales.DocumentoInstitucionalService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Documentos institucionales: el listado completo (con chulo de leído) que alimenta
 * también el carrusel del portal (ver {@code MuroController}), y su administración.
 */
@Controller
public class DocumentoInstitucionalController {

    private final DocumentoInstitucionalService documentoService;
    private final DocumentoInstitucionalReporteService reporteService;

    public DocumentoInstitucionalController(DocumentoInstitucionalService documentoService,
                                            DocumentoInstitucionalReporteService reporteService) {
        this.documentoService = documentoService;
        this.reporteService = reporteService;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    @GetMapping("/mi-espacio/documentos-institucionales")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_VER + "')")
    public String listado(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("documentos", documentoService.publicados());
        modelo.addAttribute("vistos", documentoService.vistosPor(yo));
        modelo.addAttribute("seccion", "documentos-institucionales");
        return "documentos-institucionales/listado";
    }

    @GetMapping("/mi-espacio/documentos-institucionales/{id}/ver")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_VER + "')")
    public ResponseEntity<InputStreamResource> ver(@Actor Usuario yo, @PathVariable("id") String id) {
        ArchivoDescargado archivo;
        try {
            archivo = documentoService.descargar(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        documentoService.marcarVisto(yo, id);

        String mime = archivo.mime() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : archivo.mime();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.nombreArchivo() + "\"")
                .contentLength(archivo.tamanoBytes())
                .body(new InputStreamResource(archivo.contenido()));
    }

    // ── Administración (Talento Humano) ──────────────────────────────────────

    @GetMapping("/talento-humano/documentos-institucionales")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String listar(Model modelo) {
        modelo.addAttribute("documentos", documentoService.listarTodos());
        modelo.addAttribute("seccion", "documentos-institucionales-admin");
        return "documentos-institucionales/admin/lista";
    }

    @GetMapping("/talento-humano/documentos-institucionales/nuevo")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String formularioNuevo(Model modelo) {
        modelo.addAttribute("seccion", "documentos-institucionales-admin");
        return "documentos-institucionales/admin/formulario";
    }

    @PostMapping("/talento-humano/documentos-institucionales")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String subir(@Actor Usuario actor,
                        @RequestParam("titulo") String titulo,
                        @RequestParam(value = "descripcion", required = false) String descripcion,
                        @RequestParam(value = "categoria", required = false) String categoria,
                        @RequestParam(value = "obligatorio", required = false) boolean obligatorio,
                        @RequestParam("archivo") MultipartFile archivo,
                        @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                        RedirectAttributes redirect) {
        try {
            documentoService.subir(actor, titulo, descripcion, categoria, obligatorio, archivo, miniatura);
            redirect.addFlashAttribute("aviso", "Documento creado. Publícalo cuando esté listo.");
            return "redirect:/talento-humano/documentos-institucionales";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/talento-humano/documentos-institucionales/nuevo";
        }
    }

    @GetMapping("/talento-humano/documentos-institucionales/{id}/editar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String formularioEditar(@PathVariable("id") String id, Model modelo) {
        DocumentoInstitucional documento;
        try {
            documento = documentoService.obtenerParaAdministrar(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/talento-humano/documentos-institucionales";
        }
        modelo.addAttribute("documento", documento);
        modelo.addAttribute("seccion", "documentos-institucionales-admin");
        return "documentos-institucionales/admin/formulario";
    }

    @PostMapping("/talento-humano/documentos-institucionales/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String actualizar(@Actor Usuario actor, @PathVariable("id") String id,
                             @RequestParam("titulo") String titulo,
                             @RequestParam(value = "descripcion", required = false) String descripcion,
                             @RequestParam(value = "categoria", required = false) String categoria,
                             @RequestParam(value = "obligatorio", required = false) boolean obligatorio,
                             @RequestParam(value = "archivo", required = false) MultipartFile archivo,
                             @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                             RedirectAttributes redirect) {
        try {
            documentoService.actualizar(actor, id, titulo, descripcion, categoria, obligatorio, archivo, miniatura);
            redirect.addFlashAttribute("aviso", "Documento actualizado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/documentos-institucionales/" + id + "/editar";
    }

    @GetMapping("/mi-espacio/documentos-institucionales/{id}/miniatura")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_VER + "')")
    public ResponseEntity<InputStreamResource> miniatura(@PathVariable("id") String id) {
        try {
            ArchivoDescargado archivo = documentoService.miniatura(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            archivo.mime() == null ? "image/jpeg" : archivo.mime()))
                    .contentLength(archivo.tamanoBytes())
                    .body(new InputStreamResource(archivo.contenido()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/talento-humano/documentos-institucionales/{id}/publicar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String publicar(@Actor Usuario actor, @PathVariable("id") String id,
                           @RequestParam("publicado") boolean publicado,
                           RedirectAttributes redirect) {
        try {
            DocumentoInstitucional documento = documentoService.publicar(actor, id, publicado);
            redirect.addFlashAttribute("aviso", documento.isPublicado() ? "Documento publicado." : "Documento despublicado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/documentos-institucionales";
    }

    @PostMapping("/talento-humano/documentos-institucionales/{id}/archivar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String archivar(@Actor Usuario actor, @PathVariable("id") String id, RedirectAttributes redirect) {
        try {
            documentoService.archivar(actor, id);
            redirect.addFlashAttribute("aviso", "Documento archivado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/documentos-institucionales";
    }

    @PostMapping("/talento-humano/documentos-institucionales/{id}/mover")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_INSTITUCIONAL_ADMINISTRAR + "')")
    public String mover(@Actor Usuario actor, @PathVariable("id") String id,
                        @RequestParam("direccion") String direccion,
                        RedirectAttributes redirect) {
        try {
            documentoService.mover(actor, id, "arriba".equals(direccion));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/documentos-institucionales";
    }

    @GetMapping("/talento-humano/documentos-institucionales/reporte.xlsx")
    @PreAuthorize("@capacidades.puede('" + Capacidades.REPORTE_GENERAR + "')")
    public ResponseEntity<byte[]> reporte() {
        byte[] libro = reporteService.generarConsolidado();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documentos-institucionales.xlsx\"")
                .body(libro);
    }
}
