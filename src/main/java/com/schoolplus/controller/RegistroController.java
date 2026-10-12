package com.schoolplus.controller;

import com.schoolplus.dto.RegistroForm;
import com.schoolplus.model.EventoAcceso;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import com.schoolplus.services.auth.RegisterService;
import com.schoolplus.services.auth.ResultadoRegistro;
import com.schoolplus.services.auth.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Auto-registro de cuenta y verificación por correo.
 *
 * <p>Origen del flujo: un proyecto de referencia (controller/RegisterController.java).</p>
 */
@Controller
public class RegistroController {

    private final RegisterService registerService;
    private final ValidationService validationService;
    private final AuditoriaAccesoService auditoria;

    public RegistroController(RegisterService registerService,
                              ValidationService validationService,
                              AuditoriaAccesoService auditoria) {
        this.registerService = registerService;
        this.validationService = validationService;
        this.auditoria = auditoria;
    }

    @GetMapping("/registro")
    public String formulario(Model modelo) {
        if (!modelo.containsAttribute("registroForm")) {
            modelo.addAttribute("registroForm", new RegistroForm());
        }
        cargarCatalogos(modelo);
        return "auth/registro";
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute("registroForm") RegistroForm form,
                            HttpServletRequest peticion,
                            Model modelo) {

        ResultadoRegistro resultado = registerService.registrar(form);

        if (!resultado.exitoso()) {
            form.limpiarPasswords();
            modelo.addAttribute("errores", resultado.errores());
            cargarCatalogos(modelo);
            return "auth/registro";
        }

        auditoria.registrar(EventoAcceso.REGISTRO_SOLICITADO, form.getEmail(), peticion);

        modelo.addAttribute("antetitulo", "Registro");
        modelo.addAttribute("titulo", "Revisa tu correo");
        modelo.addAttribute("mensaje",
                "Enviamos un enlace de verificación a " + form.getEmail()
                        + ". Ábrelo para activar tu cuenta; vence en 24 horas.");
        modelo.addAttribute("enlaceDesarrollo", resultado.enlaceDesarrollo());
        modelo.addAttribute("accionTexto", "Ir al inicio de sesión");
        modelo.addAttribute("accionUrl", "/");
        return "auth/mensaje";
    }

    @GetMapping("/verificar")
    public String verificar(@RequestParam(name = "token", required = false) String token,
                            HttpServletRequest peticion,
                            Model modelo) {

        Optional<String> error = registerService.verificarCuenta(token);

        if (error.isEmpty()) {
            auditoria.registrar(EventoAcceso.CUENTA_VERIFICADA, null, peticion);
            return "redirect:/?verificada=true";
        }

        modelo.addAttribute("antetitulo", "Verificación");
        modelo.addAttribute("titulo", "No pudimos activar la cuenta");
        modelo.addAttribute("mensaje", error.get());
        modelo.addAttribute("accionTexto", "Volver a registrarme");
        modelo.addAttribute("accionUrl", "/registro");
        return "auth/mensaje";
    }

    private void cargarCatalogos(Model modelo) {
        modelo.addAttribute("longitudMinimaPassword", validationService.getLongitudMinima());
    }
}
