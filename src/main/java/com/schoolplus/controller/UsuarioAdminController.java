package com.schoolplus.controller;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.UsuarioFoto;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.cuenta.CuentaService;
import com.schoolplus.services.usuarios.DatosUsuario;
import com.schoolplus.services.usuarios.DatosUsuarioInvalidosException;
import com.schoolplus.services.usuarios.UsuarioAdminService;
import org.springframework.http.CacheControl;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El módulo de usuarios que rectoría y ADMIN operan: alta, edición, activar/desactivar,
 * roles y foto de perfil, todo desde una sola ficha.
 *
 * <p>No hay borrado físico ni aquí ni en {@link UsuarioAdminService}: "eliminar" es
 * desactivar. La contraseña tampoco se toca desde este panel — nace en {@code null} y la
 * persona la fija por "recuperar contraseña".</p>
 */
@Controller
@RequestMapping("/rectoria/usuarios")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CuentaService cuentaService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService,
                                  UsuarioRepository usuarioRepository,
                                  RolRepository rolRepository,
                                  CuentaService cuentaService) {
        this.usuarioAdminService = usuarioAdminService;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.cuentaService = cuentaService;
    }

    // ── Listado y alta ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String lista(Model modelo) {
        modelo.addAttribute("usuarios", usuarioRepository.findAllByOrderByNombreAsc());
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String nuevoForm(Model modelo) {
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/nuevo";
    }

    @PostMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String crear(@RequestParam String nombre, @RequestParam String email,
                        @RequestParam String cedula, @RequestParam String telefono,
                        @RequestParam(required = false) String fechaNacimiento,
                        RedirectAttributes redirect) {
        LocalDate nacimiento;
        try {
            nacimiento = parsearFecha(fechaNacimiento);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/rectoria/usuarios/nuevo";
        }
        try {
            Usuario creado = usuarioAdminService.crear(
                    new DatosUsuario(nombre, email, cedula, telefono, nacimiento));
            redirect.addFlashAttribute("aviso", "Usuario creado. La persona fija su contraseña con \"Olvidé mi contraseña\".");
            return "redirect:/rectoria/usuarios/" + creado.getId();
        } catch (DatosUsuarioInvalidosException e) {
            redirect.addFlashAttribute("error", String.join(" ", e.getErrores()));
            return "redirect:/rectoria/usuarios/nuevo";
        }
    }

    // ── Ficha ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String detalle(@PathVariable String id, Model modelo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + id + " no existe."));

        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("tieneFoto", cuentaService.tieneFoto(id));
        modelo.addAttribute("roles", rolRepository.findAll());
        // Por codigo, no por igualdad de entidad: usuario y roles vienen de consultas
        // (y por lo tanto de contextos de persistencia) distintos, así que dos objetos
        // Rol que representan la misma fila no son == ni .equals() entre sí.
        modelo.addAttribute("codigosRolDelUsuario",
                usuario.getRoles().stream().map(Rol::getCodigo).collect(Collectors.toSet()));
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/detalle";
    }

    @PostMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String actualizar(@PathVariable String id,
                             @RequestParam String nombre, @RequestParam String email,
                             @RequestParam String cedula, @RequestParam String telefono,
                             @RequestParam(required = false) String fechaNacimiento,
                             RedirectAttributes redirect) {
        LocalDate nacimiento;
        try {
            nacimiento = parsearFecha(fechaNacimiento);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/rectoria/usuarios/" + id;
        }
        try {
            usuarioAdminService.actualizar(id, new DatosUsuario(nombre, email, cedula, telefono, nacimiento));
            redirect.addFlashAttribute("aviso", "Datos actualizados.");
        } catch (DatosUsuarioInvalidosException e) {
            redirect.addFlashAttribute("error", String.join(" ", e.getErrores()));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/usuarios/" + id;
    }

    /** Vacío o ausente es "no hay fecha"; cualquier otra cosa debe parsear como ISO-8601 o falla. */
    private LocalDate parsearFecha(String texto) {
        return (texto == null || texto.isBlank()) ? null : LocalDate.parse(texto);
    }

    @PostMapping("/{id}/estado")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String cambiarEstado(@PathVariable String id, @RequestParam EstadoUsuario nuevoEstado,
                                RedirectAttributes redirect) {
        usuarioAdminService.cambiarEstado(id, nuevoEstado);
        redirect.addFlashAttribute("aviso",
                nuevoEstado == EstadoUsuario.ACTIVO ? "Usuario activado." : "Usuario desactivado.");
        return "redirect:/rectoria/usuarios/" + id;
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String actualizarRoles(@PathVariable String id,
                                  @RequestParam(value = "roles", required = false) List<String> roles,
                                  RedirectAttributes redirect) {
        try {
            usuarioAdminService.asignarRoles(id, roles == null ? Set.of() : Set.copyOf(roles));
            redirect.addFlashAttribute("aviso", "Roles actualizados.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/usuarios/" + id;
    }

    // ── Foto de perfil ───────────────────────────────────────────────────────

    @PostMapping("/{id}/foto")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String subirFoto(@PathVariable String id, @RequestParam("archivo") MultipartFile archivo,
                            RedirectAttributes redirect) {
        try {
            cuentaService.subirFoto(id, archivo);
            redirect.addFlashAttribute("aviso", "Foto actualizada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/rectoria/usuarios/" + id;
    }

    @PostMapping("/{id}/foto/eliminar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String eliminarFoto(@PathVariable String id, RedirectAttributes redirect) {
        cuentaService.eliminarFoto(id);
        redirect.addFlashAttribute("aviso", "Foto eliminada.");
        return "redirect:/rectoria/usuarios/" + id;
    }

    @GetMapping("/{id}/foto")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public ResponseEntity<byte[]> foto(@PathVariable String id) {
        UsuarioFoto foto = cuentaService.obtenerFoto(id).orElse(null);
        if (foto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
                .cacheControl(CacheControl.noCache())
                .body(foto.getContenido());
    }
}
