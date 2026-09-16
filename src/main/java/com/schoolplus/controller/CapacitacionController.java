package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.CursoDetalle;
import com.schoolplus.dto.PreguntasForm;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Curso;
import com.schoolplus.model.TipoCurso;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.capacitacion.CursoReporteService;
import com.schoolplus.services.capacitacion.CursoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Capacitaciones: catálogo con carrusel, un curso con su video y su quiz, y la
 * administración del catálogo por Talento Humano (§ capacitaciones).
 *
 * <p>La inducción no tiene rutas propias: es un {@link Curso} con
 * {@code tipo = INDUCCION}, servido por las mismas pantallas.</p>
 */
@Controller
public class CapacitacionController {

    private static final int TAMANO_BUFER_RANGO = 1024 * 1024; // 1MB por defecto si el navegador no pide rango

    private final CursoService cursoService;
    private final CursoReporteService reporteService;

    public CapacitacionController(CursoService cursoService, CursoReporteService reporteService) {
        this.cursoService = cursoService;
        this.reporteService = reporteService;
    }

    // ── Catálogo (colaborador) ───────────────────────────────────────────────

    @GetMapping("/mi-espacio/capacitaciones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public String catalogo(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("cursos", cursoService.catalogo());
        modelo.addAttribute("induccion", cursoService.cursoInduccionObligatorio().orElse(null));
        modelo.addAttribute("inscripcionPorCurso", cursoService.misInscripciones(yo).stream()
                .collect(java.util.stream.Collectors.toMap(i -> i.getCurso().getId(), i -> i)));
        modelo.addAttribute("seccion", "capacitaciones");
        return "capacitaciones/catalogo";
    }

    @GetMapping("/mi-espacio/capacitaciones/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public String curso(@Actor Usuario yo, @PathVariable("id") String id, Model modelo) {
        CursoDetalle detalle;
        try {
            detalle = cursoService.detalle(yo, id);
        } catch (IllegalArgumentException e) {
            return "redirect:/mi-espacio/capacitaciones";
        }
        modelo.addAttribute("detalle", detalle);
        modelo.addAttribute("seccion", "capacitaciones");
        return "capacitaciones/curso";
    }

    @PostMapping("/mi-espacio/capacitaciones/{id}/video-visto")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public String videoVisto(@Actor Usuario yo, @PathVariable("id") String id, RedirectAttributes redirect) {
        try {
            cursoService.marcarVideoVisto(yo, id);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-espacio/capacitaciones/" + id;
    }

    @PostMapping("/mi-espacio/capacitaciones/{id}/quiz")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public String presentarQuiz(@Actor Usuario yo, @PathVariable("id") String id,
                                @RequestParam Map<String, String> parametros,
                                RedirectAttributes redirect) {
        Map<String, String> respuestas = new HashMap<>();
        parametros.forEach((clave, valor) -> {
            if (clave.startsWith("respuesta-")) {
                respuestas.put(clave.substring("respuesta-".length()), valor);
            }
        });
        try {
            var intento = cursoService.presentarQuiz(yo, id, respuestas);
            redirect.addFlashAttribute("aviso", intento.isAprobado()
                    ? "¡Aprobaste el quiz! Nota: " + intento.getNota()
                    : "No alcanzaste la nota mínima. Nota: " + intento.getNota() + ". Puedes volver a intentarlo.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-espacio/capacitaciones/" + id;
    }

    // ── Video con soporte de Range (206) ─────────────────────────────────────

    @GetMapping("/mi-espacio/capacitaciones/{id}/video")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public ResponseEntity<InputStreamResource> video(@PathVariable("id") String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rango) {
        Curso curso;
        try {
            curso = cursoService.obtenerParaVideo(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        long tamanoTotal = curso.getVideoTamanoBytes() == null ? 0 : curso.getVideoTamanoBytes();
        String mime = curso.getVideoMime() == null ? "video/mp4" : curso.getVideoMime();

        if (rango == null || !rango.startsWith("bytes=") || tamanoTotal <= 0) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(tamanoTotal)
                    .body(new InputStreamResource(cursoService.streamVideo(curso, null, null)));
        }

        long[] limites = parsearRango(rango, tamanoTotal);
        long inicio = limites[0];
        long fin = limites[1];
        long longitud = fin - inicio + 1;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + inicio + "-" + fin + "/" + tamanoTotal)
                .contentLength(longitud)
                .body(new InputStreamResource(cursoService.streamVideo(curso, inicio, longitud)));
    }

    @GetMapping("/mi-espacio/capacitaciones/{id}/miniatura")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_VER + "')")
    public ResponseEntity<InputStreamResource> miniatura(@PathVariable("id") String id) {
        try {
            var archivo = cursoService.miniatura(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            archivo.mime() == null ? "image/jpeg" : archivo.mime()))
                    .contentLength(archivo.tamanoBytes())
                    .body(new InputStreamResource(archivo.contenido()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private long[] parsearRango(String encabezado, long tamanoTotal) {
        try {
            String[] partes = encabezado.substring("bytes=".length()).split("-", 2);
            long inicio = partes[0].isBlank() ? 0 : Long.parseLong(partes[0].trim());
            long fin = (partes.length > 1 && !partes[1].isBlank())
                    ? Long.parseLong(partes[1].trim())
                    : Math.min(inicio + TAMANO_BUFER_RANGO - 1, tamanoTotal - 1);
            fin = Math.min(fin, tamanoTotal - 1);
            if (inicio > fin) {
                inicio = 0;
                fin = tamanoTotal - 1;
            }
            return new long[]{inicio, fin};
        } catch (NumberFormatException e) {
            return new long[]{0, tamanoTotal - 1};
        }
    }

    // ── Administración (Talento Humano) ──────────────────────────────────────

    @GetMapping("/talento-humano/capacitaciones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String listar(Model modelo) {
        modelo.addAttribute("cursos", cursoService.listarTodos());
        modelo.addAttribute("seccion", "capacitaciones-admin");
        return "capacitaciones/admin/lista";
    }

    @GetMapping("/talento-humano/capacitaciones/nuevo")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String formularioNuevo(Model modelo) {
        modelo.addAttribute("tipos", TipoCurso.values());
        modelo.addAttribute("seccion", "capacitaciones-admin");
        return "capacitaciones/admin/formulario";
    }

    @PostMapping("/talento-humano/capacitaciones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String crear(@Actor Usuario actor,
                        @RequestParam("titulo") String titulo,
                        @RequestParam(value = "descripcion", required = false) String descripcion,
                        @RequestParam("tipo") TipoCurso tipo,
                        @RequestParam(value = "obligatorio", required = false) boolean obligatorio,
                        @RequestParam("video") MultipartFile video,
                        @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                        RedirectAttributes redirect) {
        try {
            Curso curso = cursoService.crear(actor, titulo, descripcion, tipo, obligatorio, video, miniatura);
            redirect.addFlashAttribute("aviso", "Curso creado. Agrégale el quiz y publícalo cuando esté listo.");
            return "redirect:/talento-humano/capacitaciones/" + curso.getId() + "/editar";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/talento-humano/capacitaciones/nuevo";
        }
    }

    @GetMapping("/talento-humano/capacitaciones/{id}/editar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String formularioEditar(@PathVariable("id") String id, Model modelo) {
        Curso curso;
        try {
            curso = cursoService.obtenerParaAdministrar(id);
        } catch (IllegalArgumentException e) {
            return "redirect:/talento-humano/capacitaciones";
        }
        modelo.addAttribute("curso", curso);
        modelo.addAttribute("tipos", TipoCurso.values());
        modelo.addAttribute("preguntasForm", PreguntasForm.desde(cursoService.preguntasActuales(id)));
        modelo.addAttribute("seccion", "capacitaciones-admin");
        return "capacitaciones/admin/formulario";
    }

    @PostMapping("/talento-humano/capacitaciones/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String actualizar(@Actor Usuario actor, @PathVariable("id") String id,
                             @RequestParam("titulo") String titulo,
                             @RequestParam(value = "descripcion", required = false) String descripcion,
                             @RequestParam(value = "obligatorio", required = false) boolean obligatorio,
                             @RequestParam(value = "video", required = false) MultipartFile video,
                             @RequestParam(value = "miniatura", required = false) MultipartFile miniatura,
                             RedirectAttributes redirect) {
        try {
            cursoService.actualizar(actor, id, titulo, descripcion, obligatorio, video, miniatura);
            redirect.addFlashAttribute("aviso", "Curso actualizado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/capacitaciones/" + id + "/editar";
    }

    @PostMapping("/talento-humano/capacitaciones/{id}/preguntas")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String guardarPreguntas(@Actor Usuario actor, @PathVariable("id") String id,
                                   @ModelAttribute PreguntasForm preguntasForm,
                                   RedirectAttributes redirect) {
        try {
            cursoService.reemplazarPreguntas(actor, id, preguntasForm.aEntradas());
            redirect.addFlashAttribute("aviso", "Quiz guardado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/capacitaciones/" + id + "/editar";
    }

    @PostMapping("/talento-humano/capacitaciones/{id}/publicar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public String publicar(@Actor Usuario actor, @PathVariable("id") String id,
                           @RequestParam("publicado") boolean publicado,
                           RedirectAttributes redirect) {
        try {
            cursoService.publicar(actor, id, publicado);
            redirect.addFlashAttribute("aviso", publicado ? "Curso publicado." : "Curso despublicado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/capacitaciones";
    }

    @GetMapping("/talento-humano/capacitaciones/reporte.xlsx")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CAPACITACION_ADMINISTRAR + "')")
    public ResponseEntity<byte[]> reporte() {
        byte[] libro = reporteService.generarConsolidado();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"capacitaciones.xlsx\"")
                .body(libro);
    }
}
