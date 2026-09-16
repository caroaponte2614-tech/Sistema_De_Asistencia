package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.RegistroReconocimiento;
import com.schoolplus.model.TipoReconocimiento;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.reconocimientos.NotificadorReconocimientos;
import com.schoolplus.services.reconocimientos.ReconocimientoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Reconocimientos y llamados de atención: el colaborador ve los suyos, coordinador y
 * Talento Humano registran sobre su gente. Una sola ruta ({@code /personas/{id}/...})
 * sirve a ambos roles porque {@code puedeSobre} ya discrimina ámbito AREA de TODOS.
 */
@Controller
public class ReconocimientoController {

    private final ReconocimientoService reconocimientoService;
    private final NotificadorReconocimientos notificador;
    private final UsuarioRepository usuarioRepository;

    public ReconocimientoController(ReconocimientoService reconocimientoService,
                                    NotificadorReconocimientos notificador,
                                    UsuarioRepository usuarioRepository) {
        this.reconocimientoService = reconocimientoService;
        this.notificador = notificador;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/mi-espacio/reconocimientos")
    @PreAuthorize("@capacidades.puede('" + Capacidades.RECONOCIMIENTO_VER + "')")
    public String misRegistros(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("registros", reconocimientoService.misRegistros(yo));
        modelo.addAttribute("seccion", "reconocimientos");
        return "reconocimientos/mis-registros";
    }

    @GetMapping("/personas/{colaboradorId}/reconocimientos")
    @PreAuthorize("@capacidades.puede('" + Capacidades.RECONOCIMIENTO_VER + "')")
    @Transactional(readOnly = true)
    public String historial(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario yo, Model modelo) {
        Usuario colaborador;
        try {
            colaborador = usuarioRepository.findById(colaboradorId)
                    .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));
            modelo.addAttribute("registros", reconocimientoService.historialDe(yo, colaboradorId));
        } catch (IllegalArgumentException e) {
            return "redirect:/coordinacion/equipo";
        }
        modelo.addAttribute("colaborador", colaborador);
        modelo.addAttribute("tipos", TipoReconocimiento.values());
        modelo.addAttribute("seccion", "equipo");
        return "reconocimientos/historial";
    }

    @PostMapping("/personas/{colaboradorId}/reconocimientos")
    @PreAuthorize("@capacidades.puede('" + Capacidades.RECONOCIMIENTO_CREAR + "')")
    public String registrar(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario actor,
                            @RequestParam("tipo") TipoReconocimiento tipo,
                            @RequestParam(value = "categoria", required = false) String categoria,
                            @RequestParam("titulo") String titulo,
                            @RequestParam("descripcion") String descripcion,
                            @RequestParam("fecha") String fechaTexto,
                            @RequestParam(value = "soporte", required = false) MultipartFile soporte,
                            RedirectAttributes redirect) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTexto);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida.");
            return "redirect:/personas/" + colaboradorId + "/reconocimientos";
        }

        try {
            RegistroReconocimiento registro = reconocimientoService.registrar(
                    actor, colaboradorId, tipo, categoria, titulo, descripcion, fecha, soporte);
            notificador.avisarNuevoRegistro(registro);
            redirect.addFlashAttribute("aviso", "Registrado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personas/" + colaboradorId + "/reconocimientos";
    }

    @GetMapping("/reconocimientos/{id}/soporte")
    @PreAuthorize("@capacidades.puede('" + Capacidades.RECONOCIMIENTO_VER + "')")
    public ResponseEntity<InputStreamResource> soporte(@Actor Usuario yo, @PathVariable("id") String id) {
        ArchivoDescargado archivo;
        try {
            archivo = reconocimientoService.descargarSoporte(yo, id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .body(new InputStreamResource(archivo.contenido()));
    }
}
