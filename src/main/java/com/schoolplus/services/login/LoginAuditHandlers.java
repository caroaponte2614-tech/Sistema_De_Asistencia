package com.schoolplus.services.login;

import com.schoolplus.model.EventoAcceso;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Los tres enganches de la bitácora de accesos: entrada correcta, entrada fallida y salida.
 *
 * <p>El manejador de éxito hereda de un proyecto de referencia el enrutado por rol; lo nuevo es que
 * además deja constancia de quién entró, desde qué IP y con qué agente (§4.1).</p>
 */
@Component
public class LoginAuditHandlers {

    private final AuditoriaAccesoService auditoria;
    private final UsuarioRepository usuarioRepository;

    public LoginAuditHandlers(AuditoriaAccesoService auditoria, UsuarioRepository usuarioRepository) {
        this.auditoria = auditoria;
        this.usuarioRepository = usuarioRepository;
    }

    public AuthenticationSuccessHandler exito() {
        return (peticion, respuesta, autenticacion) -> {
            String email = autenticacion.getName();
            String usuarioId = usuarioRepository.findByEmail(email)
                    .map(u -> u.getId())
                    .orElse(null);
            auditoria.registrar(EventoAcceso.LOGIN_OK, email, usuarioId, peticion, null);
            // Al portal, no al panel del rol: es la pestaña inicial de los cuatro (§4.7).
            respuesta.sendRedirect(peticion.getContextPath() + RutasPorRol.trasIniciarSesion());
        };
    }

    public AuthenticationFailureHandler fallo() {
        return (peticion, respuesta, excepcion) -> {
            String email = peticion.getParameter("email");
            auditoria.registrar(EventoAcceso.LOGIN_FALLIDO, email, null, peticion,
                    excepcion.getClass().getSimpleName());
            // Mensaje único en pantalla: no se distingue "no existe" de "clave incorrecta".
            respuesta.sendRedirect(peticion.getContextPath() + "/?error=true");
        };
    }

    public LogoutHandler salida() {
        return (HttpServletRequest peticion, jakarta.servlet.http.HttpServletResponse respuesta,
                Authentication autenticacion) -> {
            if (autenticacion == null) {
                return;
            }
            String email = autenticacion.getName();
            String usuarioId = usuarioRepository.findByEmail(email)
                    .map(u -> u.getId())
                    .orElse(null);
            auditoria.registrar(EventoAcceso.LOGOUT, email, usuarioId, peticion, null);
        };
    }
}
