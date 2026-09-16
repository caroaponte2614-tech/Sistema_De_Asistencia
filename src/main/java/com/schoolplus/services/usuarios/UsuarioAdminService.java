package com.schoolplus.services.usuarios;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AreaRepository;
import com.schoolplus.repository.CargoRepository;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.SedeRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auth.ValidationService;
import com.schoolplus.util.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El CRUD de usuarios que RH opera desde su panel (D-017): alta, edición, activar o
 * desactivar, y asignación de roles.
 *
 * <p><strong>No hay borrado físico.</strong> Un colaborador tiene solicitudes,
 * documentos y aprobaciones que referencian su id (FKs por todo el esquema); "eliminar"
 * es pasar {@code estado} a {@link EstadoUsuario#INACTIVO}, exactamente como ya lo
 * describe el javadoc de {@link Usuario}. La persona conserva su historial y no puede
 * volver a entrar.</p>
 *
 * <p>La contraseña no se fija aquí: nace en {@code null} —el mismo estado que ya usa la
 * migración de usuarios heredados (V1)— y la persona la establece por el flujo de
 * "recuperar contraseña" que {@code PasswordController} ya expone. No hace falta un
 * segundo mecanismo para lo mismo.</p>
 */
@Service
public class UsuarioAdminService {

    private final UsuarioRepository usuarioRepository;
    private final SedeRepository sedeRepository;
    private final CargoRepository cargoRepository;
    private final AreaRepository areaRepository;
    private final RolRepository rolRepository;
    private final ValidationService validationService;

    public UsuarioAdminService(UsuarioRepository usuarioRepository,
                               SedeRepository sedeRepository,
                               CargoRepository cargoRepository,
                               AreaRepository areaRepository,
                               RolRepository rolRepository,
                               ValidationService validationService) {
        this.usuarioRepository = usuarioRepository;
        this.sedeRepository = sedeRepository;
        this.cargoRepository = cargoRepository;
        this.areaRepository = areaRepository;
        this.rolRepository = rolRepository;
        this.validationService = validationService;
    }

    @Transactional
    public Usuario crear(DatosUsuario datos) {
        List<String> errores = validarBasicosYCatalogos(datos);
        if (usuarioRepository.existsByEmail(datos.email())) {
            errores.add("Ese correo ya tiene cuenta.");
        }
        if (usuarioRepository.existsByCedula(datos.cedula())) {
            errores.add("Esa cédula ya está registrada.");
        }
        if (!errores.isEmpty()) {
            throw new DatosUsuarioInvalidosException(errores);
        }

        Usuario usuario = new Usuario();
        usuario.setId(UuidV7.nuevoId());
        usuario.setEstado(EstadoUsuario.ACTIVO);
        aplicarDatos(usuario, datos);

        // Rol base: igual que el auto-registro, nadie nace con permisos elevados por
        // defecto. RH los sube aparte con asignarRoles, un acto deliberado.
        Rol rolBase = rolRepository.findById(Rol.USUARIO).orElseThrow(() ->
                new IllegalStateException("Falta el rol USUARIO. ¿Se aplicó la migración V1?"));
        usuario.getRoles().add(rolBase);

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(String usuarioId, DatosUsuario datos) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + usuarioId + " no existe."));

        List<String> errores = validarBasicosYCatalogos(datos);
        usuarioRepository.findByEmail(datos.email())
                .filter(otro -> !otro.getId().equals(usuarioId))
                .ifPresent(otro -> errores.add("Ese correo ya tiene cuenta."));
        usuarioRepository.findByCedula(datos.cedula())
                .filter(otro -> !otro.getId().equals(usuarioId))
                .ifPresent(otro -> errores.add("Esa cédula ya está registrada."));
        if (!errores.isEmpty()) {
            throw new DatosUsuarioInvalidosException(errores);
        }

        aplicarDatos(usuario, datos);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void cambiarEstado(String usuarioId, EstadoUsuario nuevoEstado) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + usuarioId + " no existe."));
        usuario.setEstado(nuevoEstado);
        usuarioRepository.save(usuario);
    }

    /** Reemplaza el conjunto completo de roles. Vacío deja al usuario sin ninguno. */
    @Transactional
    public void asignarRoles(String usuarioId, Set<String> codigosRol) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + usuarioId + " no existe."));

        List<Rol> roles = rolRepository.findAllById(codigosRol);
        if (roles.size() != codigosRol.size()) {
            Set<String> encontrados = roles.stream().map(Rol::getCodigo).collect(Collectors.toSet());
            List<String> faltantes = codigosRol.stream().filter(c -> !encontrados.contains(c)).toList();
            throw new IllegalArgumentException("Rol inexistente: " + String.join(", ", faltantes));
        }

        usuario.getRoles().clear();
        usuario.getRoles().addAll(roles);
        usuarioRepository.save(usuario);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private List<String> validarBasicosYCatalogos(DatosUsuario datos) {
        List<String> errores = new ArrayList<>(validationService.validarDatosBasicos(
                datos.nombre(), datos.email(), datos.cedula(), datos.telefono()));

        if (datos.sedeId() != null && sedeRepository.findById(datos.sedeId()).isEmpty()) {
            errores.add("La sede seleccionada no es válida.");
        }
        if (datos.cargoId() != null && cargoRepository.findById(datos.cargoId()).isEmpty()) {
            errores.add("El cargo seleccionado no es válido.");
        }
        if (datos.areaId() != null && areaRepository.findById(datos.areaId()).isEmpty()) {
            errores.add("El área seleccionada no es válida.");
        }
        return errores;
    }

    private void aplicarDatos(Usuario usuario, DatosUsuario datos) {
        usuario.setNombre(datos.nombre().trim().replaceAll("\\s+", " "));
        usuario.setEmail(datos.email().trim().toLowerCase());
        usuario.setCedula(datos.cedula().trim());
        usuario.setTelefono(datos.telefono().trim());
        usuario.setFechaIngreso(datos.fechaIngreso());
        usuario.setFechaNacimiento(datos.fechaNacimiento());

        usuario.setSede(datos.sedeId() == null ? null : sedeRepository.findById(datos.sedeId())
                .orElseThrow(() -> new IllegalArgumentException("La sede " + datos.sedeId() + " no existe.")));

        // Deliberadamente no toca coordinador_id aunque el cargo cambie: sería
        // sobrescribir en silencio una excepción manual que RH pudo haber puesto a mano
        // con OrganigramaService.reasignarJefe. El controlador de RH, al guardar un
        // cambio de cargo, consulta OrganigramaService.jefeSugeridoPara aparte y decide
        // si ofrece actualizar el jefe.
        usuario.setCargo(datos.cargoId() == null ? null : cargoRepository.findById(datos.cargoId())
                .orElseThrow(() -> new IllegalArgumentException("El cargo " + datos.cargoId() + " no existe.")));
        usuario.setArea(datos.areaId() == null ? null : areaRepository.findById(datos.areaId())
                .orElseThrow(() -> new IllegalArgumentException("El área " + datos.areaId() + " no existe.")));
    }
}
