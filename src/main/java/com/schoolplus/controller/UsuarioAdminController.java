package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.ClaseDocumento;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.UsuarioFoto;
import com.schoolplus.repository.AreaRepository;
import com.schoolplus.repository.CargoRepository;
import com.schoolplus.repository.DocumentoCategoriaRepository;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.SedeRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.capacitacion.CursoService;
import com.schoolplus.services.cuenta.CuentaService;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentos.DocumentoService;
import com.schoolplus.services.documentos.PermisoDocumentoException;
import com.schoolplus.services.organigrama.OrganigramaService;
import com.schoolplus.services.usuarios.DatosUsuario;
import com.schoolplus.services.usuarios.DatosUsuarioInvalidosException;
import com.schoolplus.util.Textos;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El módulo de usuarios del sistema que RH y ADMIN operan (D-021 punto 2, ampliado el
 * 2026-09-04): alta, edición, activar/desactivar, roles, jefe directo, foto de perfil y
 * los documentos (portafolio público de solo lectura + expediente restringido completo)
 * de cualquier colaborador, todo desde una sola ficha.
 *
 * <p>No hay borrado físico ni aquí ni en {@link com.schoolplus.services.usuarios.UsuarioAdminService}:
 * "eliminar" es desactivar. La contraseña tampoco se toca desde este panel — nace en
 * {@code null} y la persona la fija por "recuperar contraseña".</p>
 */
@Controller
@RequestMapping("/talento-humano/usuarios")
public class UsuarioAdminController {

    private final com.schoolplus.services.usuarios.UsuarioAdminService usuarioAdminService;
    private final UsuarioRepository usuarioRepository;
    private final SedeRepository sedeRepository;
    private final CargoRepository cargoRepository;
    private final AreaRepository areaRepository;
    private final RolRepository rolRepository;
    private final OrganigramaService organigramaService;
    private final CuentaService cuentaService;
    private final DocumentoService documentoService;
    private final DocumentoCategoriaRepository documentoCategoriaRepository;
    private final CursoService cursoService;

    public UsuarioAdminController(com.schoolplus.services.usuarios.UsuarioAdminService usuarioAdminService,
                                  UsuarioRepository usuarioRepository,
                                  SedeRepository sedeRepository,
                                  CargoRepository cargoRepository,
                                  AreaRepository areaRepository,
                                  RolRepository rolRepository,
                                  OrganigramaService organigramaService,
                                  CuentaService cuentaService,
                                  DocumentoService documentoService,
                                  DocumentoCategoriaRepository documentoCategoriaRepository,
                                  CursoService cursoService) {
        this.usuarioAdminService = usuarioAdminService;
        this.usuarioRepository = usuarioRepository;
        this.sedeRepository = sedeRepository;
        this.cargoRepository = cargoRepository;
        this.areaRepository = areaRepository;
        this.rolRepository = rolRepository;
        this.organigramaService = organigramaService;
        this.cuentaService = cuentaService;
        this.documentoService = documentoService;
        this.documentoCategoriaRepository = documentoCategoriaRepository;
        this.cursoService = cursoService;
    }

    // ── Listado y alta ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String lista(Model modelo) {
        modelo.addAttribute("usuarios", usuarioRepository.buscarTodosConCargoYCoordinador());
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String nuevoForm(Model modelo) {
        cargarCatalogos(modelo);
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/nuevo";
    }

    @PostMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String crear(@RequestParam String nombre, @RequestParam String email,
                        @RequestParam String cedula, @RequestParam String telefono,
                        @RequestParam(required = false) String sedeId,
                        @RequestParam(required = false) String cargoId,
                        @RequestParam(required = false) String areaId,
                        @RequestParam(required = false) String fechaIngreso,
                        @RequestParam(required = false) String fechaNacimiento,
                        RedirectAttributes redirect) {
        LocalDate ingreso;
        LocalDate nacimiento;
        try {
            ingreso = parsearFecha(fechaIngreso);
            nacimiento = parsearFecha(fechaNacimiento);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/talento-humano/usuarios/nuevo";
        }
        try {
            Usuario creado = usuarioAdminService.crear(new DatosUsuario(nombre, email, cedula, telefono,
                    Textos.vacioANulo(sedeId), Textos.vacioANulo(cargoId), Textos.vacioANulo(areaId), ingreso, nacimiento));
            redirect.addFlashAttribute("aviso", "Usuario creado. La persona fija su contraseña con \"Olvidé mi contraseña\".");
            return "redirect:/talento-humano/usuarios/" + creado.getId();
        } catch (DatosUsuarioInvalidosException e) {
            redirect.addFlashAttribute("error", String.join(" ", e.getErrores()));
            return "redirect:/talento-humano/usuarios/nuevo";
        }
    }

    // ── Ficha ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String detalle(@PathVariable String id, Model modelo) {
        Usuario usuario = usuarioRepository.buscarCompletoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + id + " no existe."));

        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("tieneFoto", cuentaService.tieneFoto(id));
        modelo.addAttribute("documentosPublicos", documentoService.carpetaPublicaDe(id));
        modelo.addAttribute("documentosRestringidos", documentoService.carpetaRestringidaDe(id));
        modelo.addAttribute("categoriasRestringidas",
                documentoCategoriaRepository.findByClaseAndActivoTrueOrderByNombreAsc(ClaseDocumento.RESTRINGIDO));
        modelo.addAttribute("jefeSugerido", organigramaService.jefeSugeridoPara(usuario).orElse(null));
        modelo.addAttribute("roles", rolRepository.findAll());
        // Por codigo, no por igualdad de entidad: usuario y roles vienen de consultas
        // (y por lo tanto de contextos de persistencia) distintos, así que dos objetos
        // Rol que representan la misma fila no son == ni .equals() entre sí.
        modelo.addAttribute("codigosRolDelUsuario",
                usuario.getRoles().stream().map(Rol::getCodigo).collect(Collectors.toSet()));
        modelo.addAttribute("candidatosAJefe", usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.ACTIVO));
        modelo.addAttribute("cursosInscripciones", cursoService.misInscripciones(usuario));
        cargarCatalogos(modelo);
        modelo.addAttribute("seccion", "usuarios");
        return "usuarios/detalle";
    }

    @PostMapping("/{id}")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String actualizar(@PathVariable String id,
                             @RequestParam String nombre, @RequestParam String email,
                             @RequestParam String cedula, @RequestParam String telefono,
                             @RequestParam(required = false) String sedeId,
                             @RequestParam(required = false) String cargoId,
                             @RequestParam(required = false) String areaId,
                             @RequestParam(required = false) String fechaIngreso,
                             @RequestParam(required = false) String fechaNacimiento,
                             RedirectAttributes redirect) {
        LocalDate ingreso;
        LocalDate nacimiento;
        try {
            ingreso = parsearFecha(fechaIngreso);
            nacimiento = parsearFecha(fechaNacimiento);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/talento-humano/usuarios/" + id;
        }
        try {
            usuarioAdminService.actualizar(id, new DatosUsuario(nombre, email, cedula, telefono,
                    Textos.vacioANulo(sedeId), Textos.vacioANulo(cargoId), Textos.vacioANulo(areaId), ingreso, nacimiento));
            redirect.addFlashAttribute("aviso", "Datos actualizados.");
        } catch (DatosUsuarioInvalidosException e) {
            redirect.addFlashAttribute("error", String.join(" ", e.getErrores()));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/usuarios/" + id;
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
        return "redirect:/talento-humano/usuarios/" + id;
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
        return "redirect:/talento-humano/usuarios/" + id;
    }

    @PostMapping("/{id}/jefe")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String reasignarJefe(@PathVariable String id,
                                @RequestParam(value = "nuevoJefeId", required = false) String nuevoJefeId,
                                RedirectAttributes redirect) {
        try {
            Usuario colaborador = usuarioRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("El usuario " + id + " no existe."));
            Usuario nuevoJefe = Textos.vacioANulo(nuevoJefeId) == null ? null
                    : usuarioRepository.findById(nuevoJefeId)
                            .orElseThrow(() -> new IllegalArgumentException("El jefe elegido no existe."));
            organigramaService.reasignarJefe(colaborador, nuevoJefe);
            redirect.addFlashAttribute("aviso", "Jefe directo actualizado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/usuarios/" + id;
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
        return "redirect:/talento-humano/usuarios/" + id;
    }

    @PostMapping("/{id}/foto/eliminar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String eliminarFoto(@PathVariable String id, RedirectAttributes redirect) {
        cuentaService.eliminarFoto(id);
        redirect.addFlashAttribute("aviso", "Foto eliminada.");
        return "redirect:/talento-humano/usuarios/" + id;
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

    // ── Documentos restringidos (el portafolio público solo se lee acá) ──────

    @PostMapping("/{id}/documentos/restringidos")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String subirRestringido(@PathVariable String id, @Actor Usuario actor,
                                   @RequestParam String categoriaCodigo, @RequestParam String titulo,
                                   @RequestParam(required = false) String descripcion,
                                   @RequestParam("archivo") MultipartFile archivo,
                                   RedirectAttributes redirect) {
        try {
            Usuario dueño = usuarioRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("El usuario " + id + " no existe."));
            documentoService.subirRestringido(actor, dueño, categoriaCodigo, titulo, descripcion, archivo);
            redirect.addFlashAttribute("aviso", "Documento subido.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/usuarios/" + id;
    }

    @PostMapping("/{id}/documentos/{docId}/eliminar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public String eliminarDocumento(@PathVariable String id, @PathVariable String docId,
                                    @Actor Usuario actor, RedirectAttributes redirect) {
        try {
            documentoService.eliminar(actor, docId);
            redirect.addFlashAttribute("aviso", "Documento eliminado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/talento-humano/usuarios/" + id;
    }

    @GetMapping("/{id}/documentos/{docId}/descargar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.USUARIO_ADMINISTRAR + "')")
    public ResponseEntity<InputStreamResource> descargar(@PathVariable String docId) {
        ArchivoDescargado archivo;
        try {
            archivo = documentoService.descargar(docId);
        } catch (PermisoDocumentoException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .contentLength(archivo.tamanoBytes())
                .body(new InputStreamResource(archivo.contenido()));
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void cargarCatalogos(Model modelo) {
        modelo.addAttribute("sedes", sedeRepository.findByActivoTrueOrderByNombreAsc());
        modelo.addAttribute("cargosDisponibles", cargoRepository.findByActivoTrueOrderByNombreAsc());
        modelo.addAttribute("areas", areaRepository.findByActivoTrueOrderByNombreAsc());
    }
}
