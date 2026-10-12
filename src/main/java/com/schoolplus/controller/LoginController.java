package com.schoolplus.controller;

import com.schoolplus.services.login.RutasPorRol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.schoolplus.services.asistencia.EscaneoPendiente;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantalla de inicio de sesión.
 *
 * <p>Vive en la raíz porque {@code formLogin().loginPage("/")} la usa como destino de todo
 * el que llega sin autenticar — patrón heredado de los tres repos de referencia.</p>
 *
 * <p>Aquí no hay POST: del envío del formulario se encarga el filtro de Spring Security en
 * {@code /login}. Los avisos de la pantalla llegan como parámetros de la URL
 * ({@code ?error}, {@code ?logout}, {@code ?bloqueado}…).</p>
 */
@Controller
public class LoginController {

    @GetMapping("/")
    public String login(HttpServletRequest peticion, Model modelo) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        boolean autenticado = autenticacion != null
                && autenticacion.isAuthenticated()
                && !"anonymousUser".equals(autenticacion.getPrincipal());

        // Quien ya entró no ve el formulario otra vez: va a su panel.
        if (autenticado) {
            return "redirect:" + RutasPorRol.trasIniciarSesion(autenticacion.getAuthorities());
        }

        // Llegó aquí por escanear el QR de asistencia sin sesión iniciada: se le dice por
        // qué está viendo un login en vez de su asistencia.
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null && sesion.getAttribute(EscaneoPendiente.ATRIBUTO) != null) {
            modelo.addAttribute("asistenciaPendiente", true);
        }
        return "auth/login";
    }
}
