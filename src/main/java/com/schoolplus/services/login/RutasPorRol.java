package com.schoolplus.services.login;

import com.schoolplus.model.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * A dónde aterriza cada rol después de entrar.
 *
 * <p>Origen del patrón: el {@code customSuccessHandler} de
 * un proyecto de referencia (configuration/SecurityConfig.java), que ya enrutaba por rol. Aquí se saca
 * de la configuración a una clase propia porque lo usan dos sitios: el manejador de login
 * correcto y el controlador de los paneles.</p>
 *
 * <p>El orden de evaluación importa: quien acumula roles aterriza en el de más alcance.</p>
 */
public final class RutasPorRol {

    /**
     * Dónde aterriza TODO EL MUNDO al entrar: el portal es la pestaña inicial de los
     * cuatro roles (§4.7).
     *
     * <p>No es lo mismo que el panel. Antes el login llevaba directamente al panel del
     * rol, y como ese panel no enlazaba al muro, el portal quedaba inalcanzable haciendo
     * clic aunque la ruta respondiera. El panel sigue existiendo y se llega a él desde el
     * menú lateral.</p>
     */
    public static final String PORTAL = "/portal";

    public static final String RUTA_ADMIN = "/admin";
    public static final String RUTA_SUPERVISOR = "/talento-humano";
    public static final String RUTA_COORDINADOR = "/coordinacion";
    public static final String RUTA_USUARIO = "/mi-espacio";

    /**
     * El estudiante no tiene panel ni espacio personal: lo suyo es registrar asistencia.
     * Llevarlo al portal sería darle la bienvenida a una pantalla en la que no puede hacer
     * nada.
     */
    public static final String RUTA_ESTUDIANTE = "/estudiante/asistencia";

    private RutasPorRol() {
    }

    /** Destino tras iniciar sesión. Igual para los cuatro roles del personal. */
    public static String trasIniciarSesion() {
        return PORTAL;
    }

    /** Igual, pero mandando a los estudiantes a su pantalla de asistencia. */
    public static String trasIniciarSesion(Collection<? extends GrantedAuthority> autoridades) {
        return esSoloEstudiante(autoridades) ? RUTA_ESTUDIANTE : PORTAL;
    }

    /** Estudiante y nada más. Un docente que además estudie algo sigue siendo personal. */
    public static boolean esSoloEstudiante(Collection<? extends GrantedAuthority> autoridades) {
        return tiene(autoridades, Rol.ESTUDIANTE)
                && !tiene(autoridades, Rol.ADMIN)
                && !tiene(autoridades, Rol.SUPERVISOR)
                && !tiene(autoridades, Rol.COORDINADOR)
                && !tiene(autoridades, Rol.USUARIO);
    }

    public static String destinoDe(Authentication autenticacion) {
        if (autenticacion == null) {
            return "/";
        }
        return destinoDe(autenticacion.getAuthorities());
    }

    public static String destinoDe(Collection<? extends GrantedAuthority> autoridades) {
        if (tiene(autoridades, Rol.ADMIN)) {
            return RUTA_ADMIN;
        }
        if (tiene(autoridades, Rol.SUPERVISOR)) {
            return RUTA_SUPERVISOR;
        }
        if (tiene(autoridades, Rol.COORDINADOR)) {
            return RUTA_COORDINADOR;
        }
        if (esSoloEstudiante(autoridades)) {
            return RUTA_ESTUDIANTE;
        }
        return RUTA_USUARIO;
    }

    private static boolean tiene(Collection<? extends GrantedAuthority> autoridades, String rol) {
        if (autoridades == null) {
            return false;
        }
        String buscada = "ROLE_" + rol;
        return autoridades.stream().anyMatch(a -> buscada.equals(a.getAuthority()));
    }
}
