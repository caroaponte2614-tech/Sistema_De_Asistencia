package com.schoolplus.controller;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Cargo;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.JefeCargo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CargoRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.organigrama.OrganigramaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pantalla de RH para el organigrama (D-021): "estos cargos reportan a Fulano", más la
 * excepción manual por persona. Toda la lógica y las validaciones (anti-ciclo incluido)
 * viven en {@link OrganigramaService}; este controlador solo traduce formulario ↔ servicio.
 *
 * <p>Bajo {@code /talento-humano/**}, así que {@code SecurityConfig} ya exige rol
 * SUPERVISOR a nivel de URL; el {@code @PreAuthorize} de abajo es la capacidad real
 * ({@code ORGANIGRAMA_ADMINISTRAR}, que también tiene ADMIN).</p>
 */
@Controller
@RequestMapping("/talento-humano/organigrama")
public class OrganigramaController {

    private final OrganigramaService organigramaService;
    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;

    public OrganigramaController(OrganigramaService organigramaService,
                                 CargoRepository cargoRepository,
                                 UsuarioRepository usuarioRepository) {
        this.organigramaService = organigramaService;
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.ORGANIGRAMA_ADMINISTRAR + "')")
    public String ver(@RequestParam(value = "jefeId", required = false) String jefeId, Model modelo) {
        List<Cargo> cargos = cargoRepository.findByActivoTrueOrderByNombreAsc();
        List<JefeCargo> asignaciones = organigramaService.todasLasAsignaciones();
        Set<String> cargosConJefe = asignaciones.stream().map(JefeCargo::getCargoId).collect(Collectors.toSet());

        modelo.addAttribute("usuarios", usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.ACTIVO));
        modelo.addAttribute("cargos", cargos);
        modelo.addAttribute("asignaciones", asignaciones);
        modelo.addAttribute("cargosSinJefe",
                cargos.stream().filter(c -> !cargosConJefe.contains(c.getId())).toList());
        modelo.addAttribute("excepciones", organigramaService.excepcionesManuales());

        if (jefeId != null && !jefeId.isBlank()) {
            Set<String> cargosSeleccionados = organigramaService.cargosDe(jefeId).stream()
                    .map(JefeCargo::getCargoId)
                    .collect(Collectors.toSet());
            modelo.addAttribute("jefeSeleccionado", jefeId);
            modelo.addAttribute("cargosSeleccionados", cargosSeleccionados);
        }

        modelo.addAttribute("seccion", "organigrama");
        return "talento-humano/organigrama";
    }

    @PostMapping("/asignar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ORGANIGRAMA_ADMINISTRAR + "')")
    public String asignar(@RequestParam("jefeId") String jefeId,
                          @RequestParam(value = "cargoIds", required = false) List<String> cargoIds,
                          RedirectAttributes redirect) {
        try {
            organigramaService.asignarCargos(jefeId, cargoIds == null ? Set.of() : Set.copyOf(cargoIds));
            redirect.addFlashAttribute("aviso", "Cargos actualizados.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        redirect.addAttribute("jefeId", jefeId);
        return "redirect:/talento-humano/organigrama";
    }

    @PostMapping("/reasignar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ORGANIGRAMA_ADMINISTRAR + "')")
    public String reasignar(@RequestParam("colaboradorId") String colaboradorId,
                            @RequestParam(value = "nuevoJefeId", required = false) String nuevoJefeId,
                            RedirectAttributes redirect) {
        try {
            Usuario colaborador = usuarioRepository.findById(colaboradorId)
                    .orElseThrow(() -> new IllegalArgumentException("El colaborador no existe."));
            Usuario nuevoJefe = (nuevoJefeId == null || nuevoJefeId.isBlank())
                    ? null
                    : usuarioRepository.findById(nuevoJefeId)
                            .orElseThrow(() -> new IllegalArgumentException("El jefe elegido no existe."));
            organigramaService.reasignarJefe(colaborador, nuevoJefe);
            redirect.addFlashAttribute("aviso", "Jefe directo actualizado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/organigrama";
    }
}
