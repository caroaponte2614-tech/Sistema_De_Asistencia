package com.schoolplus.controller;

import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.cuenta.CuentaService;
import com.schoolplus.services.login.RutasPorRol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Lo que el menú lateral y la barra superior necesitan en TODAS las pantallas internas:
 * a qué panel lleva el enlace "Panel" y si hay foto de perfil que pintar en vez de las
 * iniciales.
 *
 * <p>Va sobre todos los controladores del shell. Dejar fuera a alguno significaría que en
 * esa pantalla el menú pierde el enlace al panel — que es exactamente el tipo de
 * inconsistencia que se nota al navegar.</p>
 */
@ControllerAdvice(assignableTypes = {
        InicioController.class,
        CuentaController.class,
        UsuarioAdminController.class,
        AsistenciaDocenteController.class,
        AsistenciaEstudianteController.class,
        ClaseAdminController.class
})
public class ShellAdvice {

    private static final Logger log = LoggerFactory.getLogger(ShellAdvice.class);

    private final UsuarioRepository usuarioRepository;
    private final CuentaService cuentaService;

    public ShellAdvice(UsuarioRepository usuarioRepository, CuentaService cuentaService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaService = cuentaService;
    }

    @ModelAttribute("tieneFotoPerfil")
    @Transactional(readOnly = true)
    public boolean tieneFotoPerfil() {
        try {
            return usuarioRepository.findByEmail(emailDelActor())
                    .map(u -> cuentaService.tieneFoto(u.getId()))
                    .orElse(false);
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar la foto de perfil para el menú: {}", e.getMessage());
            return false;
        }
    }

    /** A dónde apunta "Panel" en el menú: cada rol al suyo. */
    @ModelAttribute("rutaPanel")
    public String rutaPanel() {
        return RutasPorRol.destinoDe(SecurityContextHolder.getContext().getAuthentication());
    }

    private String emailDelActor() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion == null ? null : autenticacion.getName();
    }
}
