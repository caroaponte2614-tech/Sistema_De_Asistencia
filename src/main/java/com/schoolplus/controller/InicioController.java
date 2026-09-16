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
 * <p>Ya NO es la primera pantalla tras el login —eso es el portal (§4.7)—: se llega desde
 * el enlace "Panel" del menú lateral.</p>
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
     * No es redundancia: si Talento Humano apaga una fila de la matriz, el panel deja
     * de abrirse sin recompilar nada. Ese es justamente el requisito del §3.1.
     */
    @GetMapping("/admin")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String admin(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Admin",
                "Administración",
                "Panel de administración",
                "Administras usuarios, roles y el organigrama, y ves todo lo que ocurre en "
                        + "el portal. Eres quien sube los documentos de expediente."));
    }

    @GetMapping("/talento-humano")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_SEGUNDA + "')")
    public String talentoHumano(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Supervisor",
                "Talento Humano",
                "Panel de Talento Humano",
                "Eres la última instancia de aprobación: lo que el coordinador aprueba llega "
                        + "aquí para quedar validado. También publicas en el muro y generas "
                        + "los reportes."));
    }

    @GetMapping("/coordinacion")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_PRIMERA + "')")
    public String coordinacion(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Coordinador",
                "Coordinación",
                "Panel de coordinación",
                "Apruebas en primera instancia las solicitudes de la gente de tu área y "
                        + "registras sus horas extras. Si deniegas, el trámite termina ahí."));
    }

    @GetMapping("/mi-espacio")
    public String miEspacio(@Actor Usuario actor, Model modelo) {
        return panel(actor, modelo, new PanelRol(
                "Colaborador",
                "Mi espacio",
                "Tu espacio de trabajo",
                "Aquí llevas tu ficha, tus documentos, tus solicitudes de permisos y "
                        + "vacaciones, y tu tablero de tareas."));
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
