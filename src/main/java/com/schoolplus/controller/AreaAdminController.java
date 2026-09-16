package com.schoolplus.controller;

import com.schoolplus.model.Area;
import com.schoolplus.model.Capacidades;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.area.AreaAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Áreas y líderes": pantalla exclusiva del tablero de área (D-030) para crear áreas y
 * elegir su líder — quien gestiona el tablero de esa área. No toca el módulo general de
 * usuarios ({@code /talento-humano/usuarios}); es deliberadamente su propio apartado.
 */
@Controller
@RequestMapping("/admin/tablero-area/administrar")
public class AreaAdminController {

    private final AreaAdminService areaAdminService;
    private final UsuarioRepository usuarioRepository;

    public AreaAdminController(AreaAdminService areaAdminService, UsuarioRepository usuarioRepository) {
        this.areaAdminService = areaAdminService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    @Transactional
    public String lista(Model modelo) {
        List<Area> areas = areaAdminService.listarTodas();

        Map<String, String> lideres = new HashMap<>();
        for (Area area : areas) {
            if (area.getCoordinadorId() != null) {
                usuarioRepository.findById(area.getCoordinadorId())
                        .ifPresent(u -> lideres.put(area.getId(), u.getNombre()));
            }
        }

        modelo.addAttribute("areas", areas);
        modelo.addAttribute("lideres", lideres);
        modelo.addAttribute("candidatos", areaAdminService.candidatosALider());
        modelo.addAttribute("seccion", "tablero-area-administrar");
        return "tablero-area/administrar";
    }

    @PostMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    public String crear(@RequestParam String nombre, RedirectAttributes redirect) {
        try {
            areaAdminService.crear(nombre);
            redirect.addFlashAttribute("aviso", "Área creada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tablero-area/administrar";
    }

    @PostMapping("/{id}/lider")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    public String asignarLider(@PathVariable String id,
                               @RequestParam(required = false) String liderId,
                               RedirectAttributes redirect) {
        try {
            areaAdminService.asignarLider(id, liderId);
            redirect.addFlashAttribute("aviso", "Líder actualizado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tablero-area/administrar";
    }

    @PostMapping("/{id}/estado")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    public String cambiarEstado(@PathVariable String id, @RequestParam boolean activo,
                                RedirectAttributes redirect) {
        try {
            areaAdminService.cambiarEstado(id, activo);
            redirect.addFlashAttribute("aviso", activo ? "Área activada." : "Área desactivada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tablero-area/administrar";
    }
}
