package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.ClaseDocente;
import com.schoolplus.dto.FiltroAsistencia;
import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Clase;
import com.schoolplus.model.EstadoAsistencia;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.asistencia.AsistenciaEnVivoHandler;
import com.schoolplus.services.asistencia.AsistenciaReporteService;
import com.schoolplus.services.asistencia.AsistenciaService;
import com.schoolplus.services.asistencia.DispositivoService;
import com.schoolplus.services.asistencia.ReglasAsistencia;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Las pantallas del docente: abrir la sesión, proyectarla, cerrarla, corregir y reportes.
 *
 * <p>Vive bajo {@code /docencia/**}, que {@code SecurityConfig} deja solo autenticado: quién
 * entra de verdad lo decide la capacidad {@code ASISTENCIA_TOMAR} de la matriz, con su
 * ámbito (PROPIO = mis clases, TODOS = todas). El mismo criterio que el resto del sistema.</p>
 *
 * <p>El aviso por WebSocket a la pantalla de proyección se manda desde aquí, después de que
 * el servicio confirmó su transacción — nunca dentro de ella.</p>
 */
@Controller
@RequestMapping("/docencia/asistencia")
public class AsistenciaDocenteController {

    private final AsistenciaService asistenciaService;
    private final AsistenciaReporteService reporteService;
    private final DispositivoService dispositivoService;
    private final AsistenciaEnVivoHandler enVivo;
    private final ReglasAsistencia reglas;

    public AsistenciaDocenteController(AsistenciaService asistenciaService,
                                       AsistenciaReporteService reporteService,
                                       DispositivoService dispositivoService,
                                       AsistenciaEnVivoHandler enVivo,
                                       ReglasAsistencia reglas) {
        this.asistenciaService = asistenciaService;
        this.reporteService = reporteService;
        this.dispositivoService = dispositivoService;
        this.enVivo = enVivo;
        this.reglas = reglas;
    }

    // ── Mis clases ───────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String misClases(@Actor Usuario actor, Model modelo) {
        List<ClaseDocente> clases = asistenciaService.misClases(actor).stream()
                .map(clase -> new ClaseDocente(
                        clase,
                        asistenciaService.horariosDe(clase.getId()),
                        asistenciaService.contarInscritos(clase.getId()),
                        asistenciaService.sesionAbiertaDe(clase.getId()).orElse(null)))
                .toList();

        modelo.addAttribute("clases", clases);
        modelo.addAttribute("pendientesDispositivo", dispositivoService.pendientesPara(actor));
        modelo.addAttribute("toleranciaPorDefecto", reglas.toleranciaPorDefectoMinutos());
        modelo.addAttribute("seccion", "asistencia-docente");
        return "asistencia/docente/clases";
    }

    @PostMapping("/clases/{claseId}/sesiones")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String abrirSesion(@PathVariable String claseId,
                              @RequestParam(value = "horarioId", required = false) String horarioId,
                              @RequestParam(value = "tolerancia", required = false) Integer tolerancia,
                              @Actor Usuario actor,
                              RedirectAttributes redirect) {
        try {
            AsistenciaSesion sesion = asistenciaService.abrir(actor, claseId, horarioId, tolerancia);
            return "redirect:/docencia/asistencia/sesiones/" + sesion.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/docencia/asistencia";
        }
    }

    // ── Proyección y cierre ──────────────────────────────────────────────────

    @GetMapping("/sesiones/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String proyeccion(@PathVariable String id, @Actor Usuario actor, Model modelo,
                             RedirectAttributes redirect) {
        try {
            var estado = asistenciaService.enVivo(actor, id);
            if (!estado.abierta()) {
                return "redirect:/docencia/asistencia/sesiones/" + id + "/detalle";
            }
            modelo.addAttribute("estado", estado);
            modelo.addAttribute("seccion", "asistencia-docente");
            return "asistencia/docente/proyeccion";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/docencia/asistencia";
        }
    }

    @PostMapping("/sesiones/{id}/cerrar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String cerrar(@PathVariable String id, @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            asistenciaService.cerrar(actor, id);
            enVivo.avisar(id);
            redirect.addFlashAttribute("aviso", "Sesión cerrada. Los que no se registraron quedaron ausentes.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docencia/asistencia/sesiones/" + id + "/detalle";
    }

    @GetMapping("/sesiones/{id}/detalle")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String detalle(@PathVariable String id, @Actor Usuario actor, Model modelo,
                          RedirectAttributes redirect) {
        try {
            modelo.addAttribute("detalle", asistenciaService.detalle(actor, id));
            modelo.addAttribute("estados", EstadoAsistencia.values());
            modelo.addAttribute("seccion", "asistencia-docente");
            return "asistencia/docente/detalle";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/docencia/asistencia";
        }
    }

    // ── Correcciones ─────────────────────────────────────────────────────────

    @PostMapping("/registros/{registroId}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String corregir(@PathVariable String registroId,
                           @RequestParam("sesionId") String sesionId,
                           @RequestParam("estado") EstadoAsistencia estado,
                           @RequestParam(value = "observacion", required = false) String observacion,
                           @Actor Usuario actor,
                           RedirectAttributes redirect) {
        try {
            asistenciaService.corregir(actor, registroId, estado, observacion);
            enVivo.avisar(sesionId);
            redirect.addFlashAttribute("aviso", "Registro actualizado.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docencia/asistencia/sesiones/" + sesionId + "/detalle";
    }

    /** Marcar a mano a quien no tiene celular, o a quien llegó después del cierre. */
    @PostMapping("/sesiones/{sesionId}/manual")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String marcarAMano(@PathVariable String sesionId,
                              @RequestParam("estudianteId") String estudianteId,
                              @RequestParam("estado") EstadoAsistencia estado,
                              @RequestParam(value = "observacion", required = false) String observacion,
                              @Actor Usuario actor,
                              RedirectAttributes redirect) {
        try {
            asistenciaService.marcarAMano(actor, sesionId, estudianteId, estado, observacion);
            enVivo.avisar(sesionId);
            redirect.addFlashAttribute("aviso", "Asistencia registrada a mano.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docencia/asistencia/sesiones/" + sesionId + "/detalle";
    }

    // ── Celulares ────────────────────────────────────────────────────────────

    @PostMapping("/dispositivos/{dispositivoId}/aprobar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String aprobarDispositivo(@PathVariable String dispositivoId, @Actor Usuario actor,
                                     RedirectAttributes redirect) {
        try {
            dispositivoService.aprobarCambio(actor, dispositivoId);
            redirect.addFlashAttribute("aviso", "Celular autorizado. El estudiante ya puede registrar con él.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docencia/asistencia";
    }

    @PostMapping("/dispositivos/{dispositivoId}/rechazar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public String rechazarDispositivo(@PathVariable String dispositivoId, @Actor Usuario actor,
                                      RedirectAttributes redirect) {
        try {
            dispositivoService.rechazarCambio(actor, dispositivoId);
            redirect.addFlashAttribute("aviso", "Solicitud rechazada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docencia/asistencia";
    }

    // ── Reportes ─────────────────────────────────────────────────────────────

    @GetMapping("/reportes")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REPORTE + "')")
    public String reportes(@RequestParam(value = "claseId", required = false) String claseId,
                           @RequestParam(value = "estudianteId", required = false) String estudianteId,
                           @RequestParam(value = "desde", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(value = "hasta", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @Actor Usuario actor, Model modelo) {
        FiltroAsistencia filtro = new FiltroAsistencia(claseId, estudianteId, desde, hasta);
        try {
            modelo.addAttribute("reporte", reporteService.generar(actor, filtro));
        } catch (IllegalArgumentException e) {
            modelo.addAttribute("error", e.getMessage());
        }
        modelo.addAttribute("clases", asistenciaService.misClases(actor));
        modelo.addAttribute("seccion", "asistencia-reportes");
        return "asistencia/docente/reportes";
    }

    @GetMapping("/reportes/excel")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REPORTE + "')")
    public ResponseEntity<byte[]> excel(@RequestParam(value = "claseId", required = false) String claseId,
                                        @RequestParam(value = "estudianteId", required = false) String estudianteId,
                                        @RequestParam(value = "desde", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(value = "hasta", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @Actor Usuario actor) {
        byte[] libro = reporteService.excel(actor, new FiltroAsistencia(claseId, estudianteId, desde, hasta));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asistencia.xlsx\"")
                .body(libro);
    }

    @GetMapping("/reportes/csv")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REPORTE + "')")
    public ResponseEntity<byte[]> csv(@RequestParam(value = "claseId", required = false) String claseId,
                                      @RequestParam(value = "estudianteId", required = false) String estudianteId,
                                      @RequestParam(value = "desde", required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(value = "hasta", required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @Actor Usuario actor) {
        byte[] csv = reporteService.csv(actor, new FiltroAsistencia(claseId, estudianteId, desde, hasta));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asistencia.csv\"")
                .body(csv);
    }
}
