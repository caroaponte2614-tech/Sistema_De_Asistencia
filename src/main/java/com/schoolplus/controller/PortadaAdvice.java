package com.schoolplus.controller;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.repository.SedeRepository;
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
 * <p>El prototipo (los prototipos de diseño (login)) traía "4.820 colaboradores / 36 sucursales /
 * v4.2" como marcador de posición. Aquí salen de la base de datos: inventar cifras en una
 * pantalla que ve toda la compañía sería exponer un dato falso como si fuera real.</p>
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
    private final SedeRepository sedeRepository;
    private final String version;

    public PortadaAdvice(UsuarioRepository usuarioRepository,
                         SedeRepository sedeRepository,
                         @Value("${app.version:0.0.0}") String version) {
        this.usuarioRepository = usuarioRepository;
        this.sedeRepository = sedeRepository;
        this.version = version;
    }

    @ModelAttribute("portadaColaboradores")
    @Transactional(readOnly = true)
    public long colaboradores() {
        try {
            return usuarioRepository.countByEstado(EstadoUsuario.ACTIVO);
        } catch (RuntimeException e) {
            log.warn("No se pudo contar los colaboradores para la portada: {}", e.getMessage());
            return 0L;
        }
    }

    @ModelAttribute("portadaSedes")
    @Transactional(readOnly = true)
    public long sedes() {
        try {
            return sedeRepository.countByActivoTrue();
        } catch (RuntimeException e) {
            log.warn("No se pudo contar las sedes para la portada: {}", e.getMessage());
            return 0L;
        }
    }

    @ModelAttribute("portadaVersion")
    public String version() {
        return version;
    }
}
