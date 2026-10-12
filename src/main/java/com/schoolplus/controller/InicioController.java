package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.PanelRol;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.inicio.PanelService;
import com.schoolplus.services.permisos.CapacidadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Los cuatro paneles, uno por rol.
 *
 * <p>Cada ruta está cerrada por rol en {@code SecurityConfig} y, además, por capacidad con
 * {@code @PreAuthorize}. Lo que se pinta son las capacidades que la matriz concede a esa
 * persona: es donde se ve, en pantalla, que el §3.1 vive en datos.</p>
 *
 * <p>Es la primera pantalla tras el login del personal; el estudiante aterriza en su
 * pantalla de asistencia.</p>
 */
@Controller
public class InicioController {

    private final PanelService panelService;
    private final CapacidadService capacidadService;

    public InicioController(PanelService panelService, CapacidadService capacidadService) {
        this.panelService = panelService;
        this.capacidadService = capacidadService;
    }

    /*
     * Doble puerta, a propósito. SecurityConfig ya exige el ROL para llegar a la URL;
     * @PreAuthorize exige además la CAPACIDAD, que sale de la tabla rol_capacidad.
     *
     * No es redundancia: si se apaga una fila de la matriz, el panel deja
     * de abrirse sin recompilar nada. Ese es justamente el requisito del §3.1.
     */
    @GetMapping("/admin")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String admin(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Admin",
                "Administración",
                "Panel de administración",
                "Administras usuarios y roles, creas las clases y ves la asistencia de toda "
                        + "la institución."));
    }

    @GetMapping("/rectoria")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String rectoria(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Rectoría",
                "Rectoría",
                "Panel de rectoría",
                "Creas las clases, matriculas a los estudiantes, administras los usuarios y "
                        + "ves los reportes de asistencia de toda la institución."));
    }

    @GetMapping("/coordinacion")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String coordinacion(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Coordinador",
                "Coordinación",
                "Panel de coordinación",
                "Tomas asistencia en tus clases con el QR y consultas los reportes de "
                        + "asistencia de tus grupos."));
    }

    @GetMapping("/mi-espacio")
    public String miEspacio(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Docente",
                "Mi espacio",
                "Tu espacio docente",
                "Desde aquí abres la asistencia de tus clases con el QR y consultas los "
                        + "reportes de tus grupos."));
    }

    @GetMapping("/sin-permiso")
    public String sinPermiso(@Actor Usuario actor, Model modelo) {
        modelo.addAttribute("usuario", panelService.resumenDe(actor.getEmail()));
        return "inicio/sin-permiso";
    }

    private String panel(Usuario actor, Model modelo, PanelRol panel) {
        modelo.addAttribute("panel", panel);
        modelo.addAttribute("usuario", panelService.resumenDe(actor.getEmail()));
        modelo.addAttribute("capacidades", capacidadService.capacidadesDelActor());
        modelo.addAttribute("seccion", "panel");
        return "inicio/panel";
    }
}
