package com.schoolplus.services.permisos;

import com.schoolplus.dto.CapacidadConcedida;
import com.schoolplus.model.Ambito;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RolCapacidadRepository;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide si el usuario autenticado puede hacer algo, consultando la matriz de permisos.
 *
 * <p>Es la pieza que convierte el §3.1 en una regla que se aplica de verdad. El prompt
 * exige que la matriz sea "tabla en BD, no {@code if} en el código", así que aquí no hay
 * ni un {@code if} por rol: se pregunta a {@code rol_capacidad} y se responde.</p>
 *
 * <p>El bean se llama {@code capacidades} para poder escribirlo en SpEL:
 * {@code @PreAuthorize("@capacidades.puede('CLASE_ADMINISTRAR')")}, y también desde
 * Thymeleaf, para no pintar botones que luego van a dar 403.</p>
 *
 * <h2>El ámbito</h2>
 * Tener una capacidad no basta: hay que saber sobre quién. {@link Ambito#PROPIO} es uno
 * mismo y {@link Ambito#TODOS} es toda la institución.
 */
@Service("capacidades")
public class CapacidadService {

    private final RolCapacidadRepository rolCapacidadRepository;
    private final UsuarioRepository usuarioRepository;

    public CapacidadService(RolCapacidadRepository rolCapacidadRepository,
                            UsuarioRepository usuarioRepository) {
        this.rolCapacidadRepository = rolCapacidadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** ¿El usuario autenticado tiene esta capacidad, con el alcance que sea? */
    @Transactional(readOnly = true)
    public boolean puede(String capacidad) {
        return ambitoDe(capacidad).isPresent();
    }

    /** Con qué alcance la tiene. Vacío si no la tiene. */
    @Transactional(readOnly = true)
    public Optional<Ambito> ambitoDe(String capacidad) {
        return capacidadesDelActor().stream()
                .filter(c -> c.codigo().equals(capacidad))
                // Si dos roles conceden la misma capacidad, gana el alcance mayor.
                .map(CapacidadConcedida::ambito)
                .max(Ambito::compareTo);
    }

    /**
     * ¿Puede ejercerla <em>sobre esta persona</em>?
     *
     * <p>Es la pregunta que evita la fuga clásica de estos portales: tener un permiso y
     * usarlo contra los datos de otro.</p>
     */
    @Transactional(readOnly = true)
    public boolean puedeSobre(String capacidad, String usuarioObjetivoId) {
        Optional<Ambito> ambito = ambitoDe(capacidad);
        if (ambito.isEmpty() || usuarioObjetivoId == null) {
            return false;
        }
        return switch (ambito.get()) {
            case TODOS -> true;
            case PROPIO -> usuarioObjetivoId.equals(idDelActor().orElse(null));
        };
    }

    /** Capacidades efectivas del usuario autenticado. Vacío si no hay sesión. */
    @Transactional(readOnly = true)
    public List<CapacidadConcedida> capacidadesDelActor() {
        return actor()
                .map(usuario -> {
                    Set<String> roles = usuario.getRoles().stream()
                            .map(Rol::getCodigo)
                            .collect(Collectors.toSet());
                    return roles.isEmpty()
                            ? List.<CapacidadConcedida>of()
                            : rolCapacidadRepository.capacidadesDeRoles(roles);
                })
                .orElseGet(List::of);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private Optional<String> idDelActor() {
        return actor().map(Usuario::getId);
    }

    private Optional<Usuario> actor() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(autenticacion.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmail(autenticacion.getName());
    }
}
