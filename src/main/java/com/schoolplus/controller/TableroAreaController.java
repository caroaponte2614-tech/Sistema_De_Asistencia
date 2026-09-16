package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Area;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Tablero;
import com.schoolplus.model.TableroColumna;
import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.tablero.TableroService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El tablero de un área (D-030): compartido por su equipo, gestionado por su líder.
 *
 * <p>Dos entradas a la misma pantalla: {@code /mi-espacio/tablero-area} (cualquier rol, las
 * áreas donde esa persona es miembro o líder) y {@code /admin/tablero-area} (solo ADMIN,
 * todas las áreas activas — "el admin ve los tableros de todos y todas las áreas"). El
 * tablero personal de {@code /mi-espacio/tablero} no se toca.</p>
 */
@Controller
public class TableroAreaController {

    private final TableroService tableroService;
    private final UsuarioRepository usuarioRepository;

    public TableroAreaController(TableroService tableroService, UsuarioRepository usuarioRepository) {
        this.tableroService = tableroService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/mi-espacio/tablero-area")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_USAR + "')")
    public String propia(@Actor Usuario actor, Model modelo) {
        List<Area> relevantes = tableroService.areasRelevantesPara(actor);
        if (relevantes.isEmpty()) {
            modelo.addAttribute("sinArea", true);
            modelo.addAttribute("seccion", "tablero-area");
            return "tablero/tablero-area";
        }
        return "redirect:/mi-espacio/tablero-area/" + relevantes.get(0).getId();
    }

    @GetMapping("/mi-espacio/tablero-area/{areaId}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_USAR + "')")
    @Transactional
    public String verPropia(@PathVariable String areaId, @Actor Usuario actor, Model modelo,
                            RedirectAttributes redirect) {
        return renderizar(areaId, actor, modelo, redirect,
                "/mi-espacio/tablero-area", tableroService.areasRelevantesPara(actor));
    }

    @GetMapping("/admin/tablero-area")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    public String todas(Model modelo, RedirectAttributes redirect) {
        List<Area> areas = tableroService.todasLasAreasActivas();
        if (areas.isEmpty()) {
            redirect.addFlashAttribute("error", "Todavía no hay ninguna área creada.");
            return "redirect:/admin/tablero-area/administrar";
        }
        return "redirect:/admin/tablero-area/" + areas.get(0).getId();
    }

    @GetMapping("/admin/tablero-area/{areaId}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_VER + "')")
    @Transactional
    public String verComoAdmin(@PathVariable String areaId, @Actor Usuario actor, Model modelo,
                               RedirectAttributes redirect) {
        return renderizar(areaId, actor, modelo, redirect,
                "/admin/tablero-area", tableroService.todasLasAreasActivas());
    }

    private String renderizar(String areaId, Usuario actor, Model modelo, RedirectAttributes redirect,
                              String rutaBase, List<Area> pestanas) {
        Tablero tablero;
        try {
            tablero = tableroService.tableroDeAreaValidado(actor, areaId);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + rutaBase;
        }
        List<TableroTarjeta> tarjetas = tableroService.tarjetasDe(tablero.getId());

        Map<String, List<TableroTarjeta>> porColumna = tarjetas.stream()
                .collect(Collectors.groupingBy(t -> t.getColumna().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        Area area = tablero.getArea();
        String liderNombre = area.getCoordinadorId() == null ? null
                : usuarioRepository.findById(area.getCoordinadorId()).map(Usuario::getNombre).orElse(null);

        modelo.addAttribute("pestanas", pestanas);
        modelo.addAttribute("areaActual", area);
        modelo.addAttribute("rutaBase", rutaBase);
        modelo.addAttribute("liderNombre", liderNombre);
        modelo.addAttribute("puedeEditar", tableroService.puedeEditarAreaBoard(actor, areaId));
        modelo.addAttribute("tablero", tablero);
        modelo.addAttribute("columnas", tablero.getColumnas().stream()
                .sorted(java.util.Comparator.comparingInt(TableroColumna::getOrden))
                .toList());
        modelo.addAttribute("tarjetasPorColumna", porColumna);
        modelo.addAttribute("ahora", java.time.LocalDateTime.now());
        modelo.addAttribute("seccion", rutaBase.startsWith("/admin") ? "tablero-area-admin" : "tablero-area");
        return "tablero/tablero-area";
    }
}
