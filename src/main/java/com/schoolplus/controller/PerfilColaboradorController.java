package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.HijoEntrada;
import com.schoolplus.dto.HijosForm;
import com.schoolplus.dto.PerfilDetalle;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.capacitacion.CursoService;
import com.schoolplus.services.perfilcolaborador.PerfilColaboradorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Identificación del colaborador: el propio lee la suya, el supervisor (coordinador o
 * Talento Humano) la edita — contacto de emergencia, hijos y evaluaciones de desempeño.
 */
@Controller
public class PerfilColaboradorController {

    private final PerfilColaboradorService perfilService;
    private final CursoService cursoService;

    public PerfilColaboradorController(PerfilColaboradorService perfilService, CursoService cursoService) {
        this.perfilService = perfilService;
        this.cursoService = cursoService;
    }

    @GetMapping("/mi-espacio/perfil")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_VER + "')")
    public String miPerfil(@Actor Usuario yo, Model modelo) {
        PerfilDetalle detalle = perfilService.obtener(yo, yo.getId());
        modelo.addAttribute("detalle", detalle);
        modelo.addAttribute("cursosInscripciones", cursoService.misInscripciones(detalle.colaborador()));
        modelo.addAttribute("puedeEditar", false);
        modelo.addAttribute("seccion", "perfil");
        return "perfil/ver";
    }

    @GetMapping("/personas/{colaboradorId}/perfil")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_ADMINISTRAR + "')")
    public String editar(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario yo, Model modelo) {
        PerfilDetalle detalle;
        try {
            detalle = perfilService.obtener(yo, colaboradorId);
        } catch (IllegalArgumentException e) {
            return "redirect:/coordinacion/equipo";
        }
        modelo.addAttribute("detalle", detalle);
        modelo.addAttribute("cursosInscripciones", cursoService.misInscripciones(detalle.colaborador()));
        modelo.addAttribute("hijosForm", HijosForm.desde(
                detalle.hijos().stream().map(h -> new HijoEntrada(h.getNombre(), h.getFechaNacimiento())).toList()));
        modelo.addAttribute("puedeEditar", true);
        modelo.addAttribute("seccion", "equipo");
        return "perfil/ver";
    }

    @PostMapping("/personas/{colaboradorId}/perfil/contacto")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_ADMINISTRAR + "')")
    public String guardarContacto(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario actor,
                                  @RequestParam(value = "contactoNombre", required = false) String contactoNombre,
                                  @RequestParam(value = "contactoParentesco", required = false) String contactoParentesco,
                                  @RequestParam(value = "contactoTelefono", required = false) String contactoTelefono,
                                  RedirectAttributes redirect) {
        try {
            perfilService.guardarDatosBasicos(actor, colaboradorId, contactoNombre, contactoParentesco, contactoTelefono);
            redirect.addFlashAttribute("aviso", "Datos guardados.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personas/" + colaboradorId + "/perfil";
    }

    @PostMapping("/personas/{colaboradorId}/perfil/hijos")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_ADMINISTRAR + "')")
    public String guardarHijos(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario actor,
                               @ModelAttribute HijosForm hijosForm, RedirectAttributes redirect) {
        try {
            perfilService.reemplazarHijos(actor, colaboradorId, hijosForm.aEntradas());
            redirect.addFlashAttribute("aviso", "Hijos actualizados.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personas/" + colaboradorId + "/perfil";
    }

    @PostMapping("/personas/{colaboradorId}/perfil/evaluaciones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_ADMINISTRAR + "')")
    public String agregarEvaluacion(@PathVariable("colaboradorId") String colaboradorId, @Actor Usuario actor,
                                    @RequestParam("fecha") String fechaTexto,
                                    @RequestParam("calificacion") String calificacion,
                                    @RequestParam(value = "comentario", required = false) String comentario,
                                    RedirectAttributes redirect) {
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTexto);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida.");
            return "redirect:/personas/" + colaboradorId + "/perfil";
        }
        try {
            perfilService.agregarEvaluacion(actor, colaboradorId, fecha, calificacion, comentario);
            redirect.addFlashAttribute("aviso", "Evaluación agregada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personas/" + colaboradorId + "/perfil";
    }
}
