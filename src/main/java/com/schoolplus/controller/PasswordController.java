package com.schoolplus.controller;

import com.schoolplus.model.EventoAcceso;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import com.schoolplus.services.auth.RateLimitService;
import com.schoolplus.services.auth.ValidationService;
import com.schoolplus.services.password.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * Recuperación y restablecimiento de contraseña.
 *
 * <p>Origen del flujo: un proyecto de referencia (rutas {@code /recover} y {@code /reset-password}).
 * Aquí se llaman {@code /recuperar} y {@code /restablecer}, y la diferencia de fondo con el
 * original es que <strong>la respuesta no depende de si el correo existe</strong>: siempre
 * es la misma pantalla, para que el formulario no sirva de enumerador de cuentas.</p>
 */
@Controller
public class PasswordController {

    private final PasswordResetService passwordResetService;
    private final ValidationService validationService;
    private final RateLimitService rateLimit;
    private final AuditoriaAccesoService auditoria;

    public PasswordController(PasswordResetService passwordResetService,
                              ValidationService validationService,
                              RateLimitService rateLimit,
                              AuditoriaAccesoService auditoria) {
        this.passwordResetService = passwordResetService;
        this.validationService = validationService;
        this.rateLimit = rateLimit;
        this.auditoria = auditoria;
    }

    // ── Pedir el enlace ──────────────────────────────────────────────────────

    @GetMapping("/recuperar")
    public String formularioRecuperar(Model modelo) {
        modelo.addAttribute("minutosVigencia", passwordResetService.getMinutosVigencia());
        return "auth/recuperar";
    }

    @PostMapping("/recuperar")
    public String recuperar(@RequestParam("email") String email,
                            HttpServletRequest peticion,
                            Model modelo) {

        String ip = auditoria.ipDe(peticion);

        if (rateLimit.recuperacionBloqueada(email, ip)) {
            auditoria.registrar(EventoAcceso.BLOQUEO_RATE_LIMIT, email, null, peticion,
                    "Demasiadas solicitudes de recuperación");
            return "redirect:/recuperar?bloqueado=true";
        }

        auditoria.registrar(EventoAcceso.RESET_SOLICITADO, email, peticion);
        Optional<String> enlaceDesarrollo = passwordResetService.solicitar(email);

        // Misma respuesta exista o no la cuenta.
        modelo.addAttribute("antetitulo", "Recuperar acceso");
        modelo.addAttribute("titulo", "Revisa tu correo");
        modelo.addAttribute("mensaje",
                "Si " + email + " tiene una cuenta activa, allí llegará el enlace para crear "
                        + "una contraseña nueva. Vence en "
                        + passwordResetService.getMinutosVigencia() + " minutos.");
        modelo.addAttribute("enlaceDesarrollo", enlaceDesarrollo.orElse(null));
        modelo.addAttribute("accionTexto", "Volver al inicio de sesión");
        modelo.addAttribute("accionUrl", "/");
        return "auth/mensaje";
    }

    // ── Canjear el enlace ────────────────────────────────────────────────────

    @GetMapping("/restablecer")
    public String formularioRestablecer(@RequestParam(name = "token", required = false) String token,
                                        Model modelo) {

        if (!passwordResetService.tokenCanjeable(token)) {
            return vistaEnlaceInvalido(modelo);
        }

        modelo.addAttribute("token", token);
        modelo.addAttribute("longitudMinimaPassword", validationService.getLongitudMinima());
        return "auth/restablecer";
    }

    @PostMapping("/restablecer")
    public String restablecer(@RequestParam("token") String token,
                              @RequestParam("password") String password,
                              @RequestParam("confirmPassword") String confirmPassword,
                              HttpServletRequest peticion,
                              Model modelo) {

        List<String> errores = passwordResetService.restablecer(token, password, confirmPassword);

        if (errores.isEmpty()) {
            auditoria.registrar(EventoAcceso.RESET_APLICADO, null, null, peticion, null);
            return "redirect:/?restablecida=true";
        }

        // Si lo que falló fue el propio enlace, no tiene sentido repintar el formulario.
        if (!passwordResetService.tokenCanjeable(token)) {
            return vistaEnlaceInvalido(modelo);
        }

        modelo.addAttribute("errores", errores);
        modelo.addAttribute("token", token);
        modelo.addAttribute("longitudMinimaPassword", validationService.getLongitudMinima());
        return "auth/restablecer";
    }

    private String vistaEnlaceInvalido(Model modelo) {
        modelo.addAttribute("antetitulo", "Recuperar acceso");
        modelo.addAttribute("titulo", "El enlace ya no sirve");
        modelo.addAttribute("mensaje",
                "Este enlace venció, ya se usó o no es válido. Pide uno nuevo: "
                        + "por seguridad, cada enlace sirve una sola vez.");
        modelo.addAttribute("accionTexto", "Pedir un enlace nuevo");
        modelo.addAttribute("accionUrl", "/recuperar");
        return "auth/mensaje";
    }
}
