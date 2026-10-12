package com.schoolplus.configuration;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Crea la primera cuenta ADMIN al arrancar, si todavía no hay ninguna.
 *
 * <p>Sin esto, una instalación limpia no tiene por dónde entrar: el auto-registro solo
 * produce docentes, y ascender a alguien exige un ADMIN que ya exista.</p>
 *
 * <p>Reglas: la contraseña <strong>nunca</strong> va en el código ni en el
 * {@code .properties}; llega por variable de entorno. Si no está definida, no se crea nada
 * y se avisa en el log. Y solo actúa cuando no hay ningún ADMIN: no toca cuentas
 * existentes ni reescribe contraseñas.</p>
 *
 * <p>Las tres cuentas de demostración de los otros roles están apagadas por defecto y
 * existen únicamente para poder validar el criterio de aceptación del hito 2 ("los 4 roles
 * entran y ven lo suyo") en un entorno de pruebas. <strong>En producción se deja
 * {@code app.bootstrap.demo-usuarios=false}.</strong></p>
 */
@Component
public class BootstrapUsuarios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUsuarios.class);

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminNombre;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean crearDemo;

    public BootstrapUsuarios(UsuarioRepository usuarioRepository,
                             RolRepository rolRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${app.bootstrap.admin-nombre:Administrador School_Plus}") String adminNombre,
                             @Value("${app.bootstrap.admin-email:}") String adminEmail,
                             @Value("${app.bootstrap.admin-password:}") String adminPassword,
                             @Value("${app.bootstrap.demo-usuarios:false}") boolean crearDemo) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminNombre = adminNombre;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.crearDemo = crearDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRolesCodigo(Rol.ADMIN)) {
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("No hay ningún usuario ADMIN y no se definieron BOOTSTRAP_ADMIN_EMAIL / "
                    + "BOOTSTRAP_ADMIN_PASSWORD. Nadie podrá entrar hasta que se creen.");
            return;
        }

        crear(adminNombre, adminEmail, adminPassword, Rol.ADMIN);
        log.warn("Creada la cuenta ADMIN inicial ({}). Cambia su contraseña en el primer acceso.",
                adminEmail.trim().toLowerCase());

        if (!crearDemo) {
            return;
        }

        log.warn("app.bootstrap.demo-usuarios=true: se crean tres cuentas de prueba con la "
                + "misma contraseña del ADMIN. NO usar esta opción en producción.");
        String dominio = adminEmail.substring(adminEmail.indexOf('@'));
        List.of(
                new String[]{"Rectoría de prueba", "supervisor" + dominio, Rol.SUPERVISOR},
                new String[]{"Coordinador de prueba", "coordinador" + dominio, Rol.COORDINADOR},
                new String[]{"Docente de prueba", "docente" + dominio, Rol.USUARIO}
        ).forEach(datos -> {
            if (!usuarioRepository.existsByEmail(datos[1])) {
                crear(datos[0], datos[1], adminPassword, datos[2]);
            }
        });
    }

    private void crear(String nombre, String email, String password, String rolCodigo) {
        Rol rol = rolRepository.findById(rolCodigo).orElseThrow(() ->
                new IllegalStateException("Falta el rol " + rolCodigo + ". ¿Se aplicó la migración V1?"));

        Usuario usuario = new Usuario();
        usuario.setId(UuidV7.nuevoId());
        usuario.setNombre(nombre);
        usuario.setEmail(email.trim().toLowerCase());
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.getRoles().add(rol);
        usuarioRepository.save(usuario);
    }
}
