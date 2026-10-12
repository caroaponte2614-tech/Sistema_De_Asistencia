package com.schoolplus.services.auth;

import com.schoolplus.dto.RegistroForm;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.UsuarioPendiente;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.UsuarioPendienteRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.email.EmailService;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Auto-registro con verificación por correo.
 *
 * <p>Origen: un proyecto de referencia (services/auth/RegisterService.java) — el único de los tres
 * repos donde el registro sigue vivo (en un proyecto de referencia está deshabilitado a propósito).
 * Se conserva el flujo entero: fila en {@code usuario_pendiente} con token de 24 horas, y
 * promoción a {@code usuario} solo cuando la persona abre el enlace.</p>
 *
 * <p>Diferencias con el original:</p>
 * <ul>
 *   <li>La cuenta nace con el rol {@code USUARIO}. Ascender a COORDINADOR, SUPERVISOR o
 *       ADMIN es un acto deliberado del panel de administración: nadie se auto-asigna
 *       permisos rellenando un formulario público.</li>
 * </ul>
 */
@Service
public class RegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterService.class);

    private static final int HORAS_VIGENCIA_TOKEN = 24;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPendienteRepository pendienteRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidationService validationService;
    private final EmailService emailService;

    public RegisterService(UsuarioRepository usuarioRepository,
                           UsuarioPendienteRepository pendienteRepository,
                           RolRepository rolRepository,
                           PasswordEncoder passwordEncoder,
                           ValidationService validationService,
                           EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.pendienteRepository = pendienteRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.validationService = validationService;
        this.emailService = emailService;
    }

    @Transactional
    public ResultadoRegistro registrar(RegistroForm form) {
        normalizar(form);

        List<String> errores = new ArrayList<>(validationService.validarRegistro(form));
        if (!errores.isEmpty()) {
            return ResultadoRegistro.conErrores(errores);
        }

        if (usuarioRepository.existsByEmail(form.getEmail())) {
            errores.add("Ese correo ya tiene cuenta. Si es tuya, entra o recupera la contraseña.");
        }
        if (usuarioRepository.existsByCedula(form.getCedula())) {
            errores.add("Esa cédula ya está registrada.");
        }
        pendienteRepository.findByCedula(form.getCedula())
                .filter(pendiente -> !pendiente.estaVencido())
                .filter(pendiente -> !pendiente.getEmail().equals(form.getEmail()))
                .ifPresent(pendiente -> errores.add(
                        "Ya hay una solicitud pendiente con esa cédula y otro correo."));

        if (!errores.isEmpty()) {
            return ResultadoRegistro.conErrores(errores);
        }

        // Reintentar el registro reemplaza la solicitud anterior en vez de acumular filas.
        pendienteRepository.findByEmail(form.getEmail()).ifPresent(pendienteRepository::delete);
        pendienteRepository.flush();

        String token = UUID.randomUUID().toString();

        UsuarioPendiente pendiente = new UsuarioPendiente();
        pendiente.setId(UuidV7.nuevoId());
        pendiente.setNombre(form.getNombre());
        pendiente.setEmail(form.getEmail());
        pendiente.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        pendiente.setCedula(form.getCedula());
        pendiente.setTelefono(form.getTelefono());
        pendiente.setToken(token);
        pendiente.setExpiraEn(LocalDateTime.now().plusHours(HORAS_VIGENCIA_TOKEN));
        pendienteRepository.save(pendiente);

        emailService.enviarVerificacionCuenta(form.getEmail(), form.getNombre(), token);

        String enlaceDesarrollo = emailService.estaHabilitado()
                ? null
                : emailService.enlaceVerificacion(token);

        return new ResultadoRegistro(List.of(), enlaceDesarrollo);
    }

    /**
     * Canjea el token del correo y crea la cuenta definitiva.
     *
     * @return el mensaje de error, o vacío si la cuenta quedó activa.
     */
    @Transactional
    public Optional<String> verificarCuenta(String token) {
        Optional<UsuarioPendiente> encontrado = pendienteRepository.findByToken(token);
        if (encontrado.isEmpty()) {
            return Optional.of("El enlace no es válido o ya fue utilizado.");
        }

        UsuarioPendiente pendiente = encontrado.get();

        if (pendiente.estaVencido()) {
            pendienteRepository.delete(pendiente);
            return Optional.of("El enlace de verificación venció. Regístrate de nuevo.");
        }
        if (usuarioRepository.existsByEmail(pendiente.getEmail())) {
            pendienteRepository.delete(pendiente);
            return Optional.of("Esta cuenta ya estaba activa. Puedes ingresar.");
        }

        Rol rolBase = rolRepository.findById(Rol.USUARIO).orElseThrow(() ->
                new IllegalStateException("Falta el rol USUARIO. ¿Se aplicó la migración V1?"));

        Usuario usuario = new Usuario();
        usuario.setId(UuidV7.nuevoId());
        usuario.setNombre(pendiente.getNombre());
        usuario.setEmail(pendiente.getEmail());
        usuario.setPasswordHash(pendiente.getPasswordHash());
        usuario.setCedula(pendiente.getCedula());
        usuario.setTelefono(pendiente.getTelefono());
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.getRoles().add(rolBase);
        usuarioRepository.save(usuario);

        pendienteRepository.delete(pendiente);

        log.info("Cuenta verificada y activada para {}", usuario.getEmail());
        return Optional.empty();
    }

    private void normalizar(RegistroForm form) {
        form.setNombre(normalizarEspacios(form.getNombre()));
        form.setEmail(form.getEmail() == null ? null : form.getEmail().trim().toLowerCase());
        form.setCedula(recortar(form.getCedula()));
        form.setTelefono(recortar(form.getTelefono()));
    }

    private static String normalizarEspacios(String valor) {
        return valor == null ? null : valor.trim().replaceAll("\\s+", " ");
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
