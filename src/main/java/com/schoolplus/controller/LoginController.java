package com.schoolplus.controller;

import com.schoolplus.services.login.RutasPorRol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
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
    public String login() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        boolean autenticado = autenticacion != null
                && autenticacion.isAuthenticated()
                && !"anonymousUser".equals(autenticacion.getPrincipal());

        // Quien ya entró no ve el formulario otra vez: va al portal.
        if (autenticado) {
            return "redirect:" + RutasPorRol.trasIniciarSesion();
        }
        return "auth/login";
    }
}
