package com.schoolplus.services.usuarios;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RolRepository;
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
 * El CRUD de usuarios que se opera desde el panel de administración: alta, edición,
 * activar o desactivar, y asignación de roles.
 *
 * <p><strong>No hay borrado físico.</strong> Un docente o estudiante tiene clases y
 * registros de asistencia que referencian su id; "eliminar" es pasar {@code estado} a
 * {@link EstadoUsuario#INACTIVO}. La persona conserva su historial y no puede volver a
 * entrar.</p>
 *
 * <p>La contraseña no se fija aquí: nace en {@code null} —el mismo estado que ya usa la
 * migración de usuarios heredados (V1)— y la persona la establece por el flujo de
 * "recuperar contraseña" que {@code PasswordController} ya expone. No hace falta un
 * segundo mecanismo para lo mismo.</p>
 */
@Service
public class UsuarioAdminService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final ValidationService validationService;

    public UsuarioAdminService(UsuarioRepository usuarioRepository,
                               RolRepository rolRepository,
                               ValidationService validationService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.validationService = validationService;
    }

    @Transactional
    public Usuario crear(DatosUsuario datos) {
        List<String> errores = validarBasicos(datos);
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
        // defecto. Se suben aparte con asignarRoles, un acto deliberado.
        Rol rolBase = rolRepository.findById(Rol.USUARIO).orElseThrow(() ->
                new IllegalStateException("Falta el rol USUARIO. ¿Se aplicó la migración V1?"));
        usuario.getRoles().add(rolBase);

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(String usuarioId, DatosUsuario datos) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario " + usuarioId + " no existe."));

        List<String> errores = validarBasicos(datos);
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

    private List<String> validarBasicos(DatosUsuario datos) {
        return new ArrayList<>(validationService.validarDatosBasicos(
                datos.nombre(), datos.email(), datos.cedula(), datos.telefono()));
    }

    private void aplicarDatos(Usuario usuario, DatosUsuario datos) {
        usuario.setNombre(datos.nombre().trim().replaceAll("\s+", " "));
        usuario.setEmail(datos.email().trim().toLowerCase());
        usuario.setCedula(datos.cedula().trim());
        usuario.setTelefono(datos.telefono().trim());
        usuario.setFechaNacimiento(datos.fechaNacimiento());
    }
}
