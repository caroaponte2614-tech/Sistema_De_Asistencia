package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.RegistroHorasExtra;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.horasextra.HorasExtraService;
import com.schoolplus.services.horasextra.NotificadorHorasExtra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Horas extra: alta por el coordinador, sus propios registros, y la bandeja de Talento
 * Humano que las aprueba o deniega (§4.6).
 *
 * <p>Igual que {@link SolicitudController}, este controlador no decide nada de negocio:
 * valida la entrada, llama a {@link HorasExtraService} y avisa. Toda la regla vive ahí.</p>
 */
@Controller
public class HorasExtraController {

    private static final Map<EstadoHorasExtra, String> ETIQUETAS = new EnumMap<>(EstadoHorasExtra.class);

    static {
        ETIQUETAS.put(EstadoHorasExtra.PENDIENTE, "Pendiente");
        ETIQUETAS.put(EstadoHorasExtra.APROBADO, "Aprobada");
        ETIQUETAS.put(EstadoHorasExtra.RECHAZADO, "Denegada");
    }

    private static final int POR_PAGINA = 50;

    private final HorasExtraService service;
    private final NotificadorHorasExtra notificador;
    private final UsuarioRepository usuarioRepository;

    public HorasExtraController(HorasExtraService service,
                                NotificadorHorasExtra notificador,
                                UsuarioRepository usuarioRepository) {
        this.service = service;
        this.notificador = notificador;
        this.usuarioRepository = usuarioRepository;
    }

    // ── Mis horas extra (quien las trabajó) ──────────────────────────────────

    @GetMapping("/mi-espacio/horas-extras")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_VER + "')")
    @Transactional(readOnly = true)
    public String misHorasExtra(@Actor Usuario yo, Model modelo) {
        Page<RegistroHorasExtra> registros = service.misRegistros(yo, PageRequest.of(0, POR_PAGINA));

        modelo.addAttribute("registros", registros.getContent());
        modelo.addAttribute("etiquetasEstado", ETIQUETAS);
        modelo.addAttribute("seccion", "horas-extra-mias");
        return "horas-extra/mis-horas-extra";
    }

    // ── Registro por el coordinador ───────────────────────────────────────────

    @GetMapping("/coordinacion/horas-extras")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_REGISTRAR + "')")
    @Transactional(readOnly = true)
    public String registradasPorMi(@Actor Usuario yo, Model modelo) {
        Page<RegistroHorasExtra> registros = service.registradosPor(yo, PageRequest.of(0, POR_PAGINA));

        modelo.addAttribute("registros", registros.getContent());
        modelo.addAttribute("etiquetasEstado", ETIQUETAS);
        modelo.addAttribute("seccion", "horas-extra-coordinacion");
        return "horas-extra/coordinacion";
    }

    @GetMapping("/coordinacion/horas-extras/nueva")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_REGISTRAR + "')")
    @Transactional(readOnly = true)
    public String formularioNueva(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("equipo", service.equipoDe(yo));
        modelo.addAttribute("tipos", service.tiposDeRecargo());
        modelo.addAttribute("seccion", "horas-extra-coordinacion");
        return "horas-extra/nueva";
    }

    @PostMapping("/coordinacion/horas-extras")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_REGISTRAR + "')")
    public String crear(@Actor Usuario yo,
                        @RequestParam("colaboradorId") String colaboradorId,
                        @RequestParam("recargoCodigo") String recargoCodigo,
                        @RequestParam("fecha") String fechaTexto,
                        @RequestParam("horas") String horasTexto,
                        @RequestParam(value = "observacion", required = false) String observacion,
                        RedirectAttributes redirect) {

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTexto);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/coordinacion/horas-extras/nueva";
        }

        BigDecimal horas;
        try {
            horas = new BigDecimal(horasTexto.trim());
        } catch (NumberFormatException | NullPointerException e) {
            redirect.addFlashAttribute("error", "Las horas tienen que ser un número.");
            return "redirect:/coordinacion/horas-extras/nueva";
        }

        try {
            RegistroHorasExtra registro = service.registrar(yo, colaboradorId, recargoCodigo, fecha, horas, observacion);

            // Fuera de la transacción del registro (§5.2), igual que Solicitudes.
            notificador.avisarPendiente(registro,
                    usuarioRepository.buscarActivosPorRol(Rol.SUPERVISOR, EstadoUsuario.ACTIVO));

            redirect.addFlashAttribute("aviso", "Horas extra registradas. Quedan pendientes de Talento Humano.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/coordinacion/horas-extras/nueva";
        }
        return "redirect:/coordinacion/horas-extras";
    }

    // ── Bandeja de Talento Humano ────────────────────────────────────────────

    @GetMapping("/talento-humano/horas-extras")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_APROBAR + "')")
    @Transactional(readOnly = true)
    public String bandeja(Model modelo) {
        Page<RegistroHorasExtra> pendientes = service.pendientes(PageRequest.of(0, POR_PAGINA));

        modelo.addAttribute("registros", pendientes.getContent());
        modelo.addAttribute("seccion", "horas-extra-bandeja");
        return "horas-extra/bandeja";
    }

    /** Historial completo: lo que cada coordinador registró, decidido o no, para que Talento Humano lo revise. */
    @GetMapping("/talento-humano/horas-extras/historial")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_APROBAR + "')")
    @Transactional(readOnly = true)
    public String historial(Model modelo) {
        Page<RegistroHorasExtra> registros = service.todos(PageRequest.of(0, POR_PAGINA));

        modelo.addAttribute("registros", registros.getContent());
        modelo.addAttribute("etiquetasEstado", ETIQUETAS);
        modelo.addAttribute("seccion", "horas-extra-historial");
        return "horas-extra/historial";
    }

    @PostMapping("/talento-humano/horas-extras/{id}/aprobar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_APROBAR + "')")
    public String aprobar(@PathVariable String id, @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            RegistroHorasExtra registro = service.aprobar(actor, id);
            notificador.avisarDecision(registro);
            redirect.addFlashAttribute("aviso", "Horas extra aprobadas.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/horas-extras";
    }

    @PostMapping("/talento-humano/horas-extras/{id}/rechazar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.HORAS_EXTRA_APROBAR + "')")
    public String rechazar(@PathVariable String id,
                           @RequestParam("motivo") String motivo,
                           @Actor Usuario actor,
                           RedirectAttributes redirect) {
        try {
            RegistroHorasExtra registro = service.rechazar(actor, id, motivo);
            notificador.avisarDecision(registro);
            redirect.addFlashAttribute("aviso", "Horas extra denegadas.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/horas-extras";
    }
}
