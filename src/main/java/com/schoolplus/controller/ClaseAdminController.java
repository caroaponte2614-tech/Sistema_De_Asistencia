package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Clase;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.asistencia.ClaseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administración de clases: crear, asignar profesor, poner el horario y matricular.
 *
 * <p>Vive bajo {@code /rectoria/**} —rectoría y ADMIN— porque es la misma gente que ya
 * administra usuarios. Quién entra de verdad lo decide la capacidad
 * {@code CLASE_ADMINISTRAR}.</p>
 */
@Controller
@RequestMapping("/rectoria/clases")
public class ClaseAdminController {

    private final ClaseService claseService;

    public ClaseAdminController(ClaseService claseService) {
        this.claseService = claseService;
    }

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String lista(Model modelo) {
        List<Clase> clases = claseService.todas();
        Map<String, Long> inscritosPorClase = new LinkedHashMap<>();
        clases.forEach(clase -> inscritosPorClase.put(clase.getId(), claseService.contarInscritos(clase.getId())));

        modelo.addAttribute("clases", clases);
        modelo.addAttribute("inscritosPorClase", inscritosPorClase);
        modelo.addAttribute("docentes", claseService.docentesDisponibles());
        modelo.addAttribute("seccion", "clases");
        return "clases/lista";
    }

    @PostMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String crear(@RequestParam("nombre") String nombre,
                        @RequestParam("asignatura") String asignatura,
                        @RequestParam(value = "grupo", required = false) String grupo,
                        @RequestParam("periodo") String periodo,
                        @RequestParam("profesorId") String profesorId,
                        @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            Clase clase = claseService.crear(actor, nombre, asignatura, grupo, periodo, profesorId);
            redirect.addFlashAttribute("aviso", "Clase creada. Agrégale el horario y matricula estudiantes.");
            return "redirect:/rectoria/clases/" + clase.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/rectoria/clases";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String detalle(@PathVariable String id, Model modelo, RedirectAttributes redirect) {
        try {
            modelo.addAttribute("clase", claseService.buscar(id));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/rectoria/clases";
        }
        modelo.addAttribute("horarios", claseService.horariosDe(id));
        modelo.addAttribute("inscritos", claseService.inscritosDe(id));
        modelo.addAttribute("docentes", claseService.docentesDisponibles());
        modelo.addAttribute("estudiantes", claseService.estudiantesDisponibles());
        modelo.addAttribute("dias", DayOfWeek.values());
        modelo.addAttribute("seccion", "clases");
        return "clases/detalle";
    }

    @PostMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String actualizar(@PathVariable String id,
                             @RequestParam("nombre") String nombre,
                             @RequestParam("asignatura") String asignatura,
                             @RequestParam(value = "grupo", required = false) String grupo,
                             @RequestParam("periodo") String periodo,
                             @RequestParam("profesorId") String profesorId,
                             @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            claseService.actualizar(actor, id, nombre, asignatura, grupo, periodo, profesorId);
            redirect.addFlashAttribute("aviso", "Clase actualizada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }

    @PostMapping("/{id}/estado")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String cambiarEstado(@PathVariable String id,
                                @RequestParam("activa") boolean activa,
                                @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            claseService.cambiarEstado(actor, id, activa);
            redirect.addFlashAttribute("aviso", activa ? "Clase activada." : "Clase desactivada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }

    // ── Horario ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/horarios")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String agregarHorario(@PathVariable String id,
                                 @RequestParam("dia") DayOfWeek dia,
                                 @RequestParam("horaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime inicio,
                                 @RequestParam("horaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime fin,
                                 @RequestParam(value = "aula", required = false) String aula,
                                 @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            claseService.agregarHorario(actor, id, dia, inicio, fin, aula);
            redirect.addFlashAttribute("aviso", "Franja agregada al horario.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }

    @PostMapping("/{id}/horarios/{horarioId}/eliminar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String quitarHorario(@PathVariable String id, @PathVariable String horarioId,
                                @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            claseService.quitarHorario(actor, horarioId);
            redirect.addFlashAttribute("aviso", "Franja eliminada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }

    // ── Matrícula ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/inscripciones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String matricular(@PathVariable String id,
                             @RequestParam(value = "estudianteId", required = false) String estudianteId,
                             @RequestParam(value = "correos", required = false) String correos,
                             @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            if (estudianteId != null && !estudianteId.isBlank()) {
                claseService.inscribir(actor, id, estudianteId);
                redirect.addFlashAttribute("aviso", "Estudiante matriculado.");
            } else {
                ClaseService.ResultadoMatricula resultado = claseService.inscribirPorCorreos(actor, id, correos);
                String mensaje = resultado.matriculados() + " estudiante(s) matriculado(s).";
                if (!resultado.sinCuenta().isEmpty()) {
                    mensaje += " Sin cuenta de estudiante: " + String.join(", ", resultado.sinCuenta()) + ".";
                }
                redirect.addFlashAttribute("aviso", mensaje);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }

    @PostMapping("/{id}/inscripciones/{estudianteId}/retirar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.CLASE_ADMINISTRAR + "')")
    public String retirar(@PathVariable String id, @PathVariable String estudianteId,
                          @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            claseService.retirar(actor, id, estudianteId);
            redirect.addFlashAttribute("aviso", "Estudiante retirado de la clase.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/clases/" + id;
    }
}
