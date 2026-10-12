package com.schoolplus.controller;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.repository.ClaseRepository;
import com.schoolplus.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Las tres cifras del panel de marca de las vistas de autenticación.
 *
 * <p>El prototipo (los prototipos de diseño (login)) traía cifras como marcador de
 * posición. Aquí salen de la base de datos: inventar cifras en una pantalla que ve toda
 * la institución sería exponer un dato falso como si fuera real.</p>
 *
 * <p>Si la consulta falla, la pantalla de login <strong>tiene que seguir apareciendo</strong>:
 * por eso los contadores caen a cero en vez de propagar la excepción.</p>
 */
@ControllerAdvice(assignableTypes = {
        LoginController.class,
        RegistroController.class,
        PasswordController.class
})
public class PortadaAdvice {

    private static final Logger log = LoggerFactory.getLogger(PortadaAdvice.class);

    private final UsuarioRepository usuarioRepository;
    private final ClaseRepository claseRepository;
    private final String version;

    public PortadaAdvice(UsuarioRepository usuarioRepository,
                         ClaseRepository claseRepository,
                         @Value("${app.version:0.0.0}") String version) {
        this.usuarioRepository = usuarioRepository;
        this.claseRepository = claseRepository;
        this.version = version;
    }

    @ModelAttribute("portadaUsuarios")
    @Transactional(readOnly = true)
    public long usuarios() {
        try {
            return usuarioRepository.countByEstado(EstadoUsuario.ACTIVO);
        } catch (RuntimeException e) {
            log.warn("No se pudo contar los usuarios para la portada: {}", e.getMessage());
            return 0L;
        }
    }

    @ModelAttribute("portadaClases")
    @Transactional(readOnly = true)
    public long clases() {
        try {
            return claseRepository.countByActivaTrue();
        } catch (RuntimeException e) {
            log.warn("No se pudo contar las clases para la portada: {}", e.getMessage());
            return 0L;
        }
    }

    @ModelAttribute("portadaVersion")
    public String version() {
        return version;
    }
}
