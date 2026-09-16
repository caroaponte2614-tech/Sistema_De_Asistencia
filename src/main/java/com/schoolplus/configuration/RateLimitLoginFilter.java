package com.schoolplus.configuration;

import com.schoolplus.model.EventoAcceso;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import com.schoolplus.services.auth.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Corta los intentos de login cuando se agota el cupo, antes de que Spring Security llegue
 * a comprobar la contraseña.
 *
 * <p>Va delante del filtro de autenticación a propósito: si se comprobara después, cada
 * intento seguiría costando un BCrypt de coste 12, que es justo el trabajo caro que un
 * ataque por fuerza bruta quiere provocar.</p>
 *
 * <p>No lleva {@code @Component}: Spring Boot registra automáticamente todo bean de tipo
 * {@code Filter} en el contenedor de servlets, y entonces se ejecutaría dos veces —una
 * suelta y otra dentro de la cadena de seguridad—. Lo instancia {@code SecurityConfig}.</p>
 */
public class RateLimitLoginFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimit;
    private final AuditoriaAccesoService auditoria;

    public RateLimitLoginFilter(RateLimitService rateLimit, AuditoriaAccesoService auditoria) {
        this.rateLimit = rateLimit;
        this.auditoria = auditoria;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !("POST".equalsIgnoreCase(peticion.getMethod())
                && "/login".equals(peticion.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String email = peticion.getParameter("email");
        String ip = auditoria.ipDe(peticion);

        if (rateLimit.loginBloqueado(email, ip)) {
            auditoria.registrar(EventoAcceso.BLOQUEO_RATE_LIMIT, email, null, peticion,
                    "Demasiados intentos de login");
            respuesta.sendRedirect(peticion.getContextPath() + "/?bloqueado=true");
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }
}
