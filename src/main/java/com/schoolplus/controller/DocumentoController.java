package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.DocumentoCategoriaRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentos.DocumentoService;
import com.schoolplus.services.documentos.PermisoDocumentoException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Mis documentos": el portafolio público de cada colaborador (hoja de vida,
 * certificados, soportes). Cada quien administra el suyo — la carpeta restringida de
 * expediente (que solo RH/ADMIN tocan) vive en el futuro panel de administración, no
 * aquí.
 *
 * <p>Sin capacidad nueva en el {@code @PreAuthorize}: {@code DOCUMENTO_PUBLICO_SUBIR} ya
 * la tienen los cuatro roles, ámbito PROPIO (V1) — es, literalmente, "cualquiera
 * administra lo suyo".</p>
 */
@Controller
@RequestMapping("/mi-espacio/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final DocumentoCategoriaRepository categoriaRepository;

    public DocumentoController(DocumentoService documentoService,
                               DocumentoCategoriaRepository categoriaRepository) {
        this.documentoService = documentoService;
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_PUBLICO_SUBIR + "')")
    public String miPortafolio(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("documentos", documentoService.carpetaPublicaDe(yo.getId()));
        modelo.addAttribute("categorias",
                categoriaRepository.findByClaseAndActivoTrueOrderByNombreAsc(ClaseDocumento.PUBLICO));
        modelo.addAttribute("seccion", "documentos");
        return "documentos/mis-documentos";
    }

    @PostMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_PUBLICO_SUBIR + "')")
    public String subir(@Actor Usuario actor,
                        @RequestParam("categoriaCodigo") String categoriaCodigo,
                        @RequestParam("titulo") String titulo,
                        @RequestParam(value = "descripcion", required = false) String descripcion,
                        @RequestParam("archivo") MultipartFile archivo,
                        RedirectAttributes redirect) {
        try {
            documentoService.subirPublico(actor, categoriaCodigo, titulo, descripcion, archivo);
            redirect.addFlashAttribute("aviso", "Documento subido.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-espacio/documentos";
    }

    @PostMapping("/{id}/eliminar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_PUBLICO_SUBIR + "')")
    public String eliminar(@Actor Usuario actor, @PathVariable("id") String id,
                           RedirectAttributes redirect) {
        try {
            documentoService.eliminar(actor, id);
            redirect.addFlashAttribute("aviso", "Documento eliminado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-espacio/documentos";
    }

    @GetMapping("/{id}/descargar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.DOCUMENTO_PUBLICO_SUBIR + "')")
    public ResponseEntity<InputStreamResource> descargar(@PathVariable("id") String id) {
        ArchivoDescargado archivo;
        try {
            archivo = documentoService.descargar(id);
        } catch (PermisoDocumentoException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .contentLength(archivo.tamanoBytes())
                .body(new InputStreamResource(archivo.contenido()));
    }
}
