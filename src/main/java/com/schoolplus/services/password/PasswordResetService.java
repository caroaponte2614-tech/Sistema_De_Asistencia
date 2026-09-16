package com.schoolplus.services.password;

import com.schoolplus.model.PasswordResetToken;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.PasswordResetTokenRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auth.ValidationService;
import com.schoolplus.services.email.EmailService;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperación de contraseña.
 *
 * <p>Origen: un proyecto de referencia (services/password/PasswordResetService.java). Se conserva lo
 * que estaba bien —token UUID, 30 minutos de vigencia, un solo enlace vivo por persona— y
 * se corrigen tres cosas (docs/DECISIONES.md):</p>
 * <ul>
 *   <li><strong>No se revela si el correo existe.</strong> El original devolvía
 *       {@code false} cuando no había usuario, lo que convertía el formulario en un
 *       enumerador de cuentas. Aquí la respuesta es la misma en ambos casos.</li>
 *   <li><strong>El token se marca como usado, no se borra.</strong> Un enlace sirve una
 *       sola vez y queda el rastro de cuándo se canjeó.</li>
 *   <li><strong>Cambiar la contraseña cierra las demás sesiones</strong> y borra los
 *       recordatorios de "mantener sesión activa". Si alguien entró con la clave vieja,
 *       cambiarla lo echa; antes no.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidationService validationService;
    private final EmailService emailService;
    private final PersistentTokenRepository recordatorios;
    private final FindByIndexNameSessionRepository<? extends Session> sesiones;
    private final int minutosVigencia;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder,
                                ValidationService validationService,
                                EmailService emailService,
                                PersistentTokenRepository recordatorios,
                                FindByIndexNameSessionRepository<? extends Session> sesiones,
                                @Value("${app.security.reset.minutos-vigencia:30}") int minutosVigencia) {
        this.tokenRepository = tokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.validationService = validationService;
        this.emailService = emailService;
        this.recordatorios = recordatorios;
        this.sesiones = sesiones;
        this.minutosVigencia = minutosVigencia;
    }

    public int getMinutosVigencia() {
        return minutosVigencia;
    }

    /**
     * Genera el enlace y lo envía.
     *
     * @return el enlace, y solo cuando el envío de correo está apagado, para poder probar
     *         el flujo sin cuenta SMTP. Nunca dice si el correo existía.
     */
    @Transactional
    public Optional<String> solicitar(String email) {
        String correo = email == null ? "" : email.trim().toLowerCase();
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(correo);

        if (encontrado.isEmpty()) {
            log.info("Solicitud de recuperación para un correo sin cuenta. No se envía nada.");
            return Optional.empty();
        }

        Usuario usuario = encontrado.get();
        if (!usuario.estaActivo()) {
            log.info("Solicitud de recuperación de una cuenta inactiva ({}). No se envía nada.", correo);
            return Optional.empty();
        }

        tokenRepository.invalidarVigentesDe(usuario.getId());

        String token = UUID.randomUUID().toString();
        PasswordResetToken registro = new PasswordResetToken();
        registro.setId(UuidV7.nuevoId());
        registro.setUsuario(usuario);
        registro.setToken(token);
        registro.setExpiraEn(LocalDateTime.now().plusMinutes(minutosVigencia));
        tokenRepository.save(registro);

        emailService.enviarRecuperacionPassword(
                usuario.getEmail(), usuario.getNombre(), token, minutosVigencia);

        return emailService.estaHabilitado()
                ? Optional.empty()
                : Optional.of(emailService.enlaceRestablecer(token));
    }

    @Transactional(readOnly = true)
    public boolean tokenCanjeable(String token) {
        return token != null
                && tokenRepository.buscarConUsuarioPorToken(token)
                .filter(PasswordResetToken::esCanjeable)
                .isPresent();
    }

    /**
     * Aplica la contraseña nueva.
     *
     * @return la lista de errores; vacía si el cambio quedó guardado.
     */
    @Transactional
    public List<String> restablecer(String token, String password, String confirmacion) {
        Optional<PasswordResetToken> encontrado = token == null
                ? Optional.empty()
                : tokenRepository.buscarConUsuarioPorToken(token);

        if (encontrado.isEmpty() || !encontrado.get().esCanjeable()) {
            return List.of("El enlace no es válido, ya se usó o venció. Pide uno nuevo.");
        }

        Usuario usuario = encontrado.get().getUsuario();

        List<String> errores = new ArrayList<>(validationService.validarPassword(
                password, confirmacion, usuario.getNombre(), usuario.getEmail()));
        if (!errores.isEmpty()) {
            return errores;
        }

        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuarioRepository.save(usuario);

        PasswordResetToken registro = encontrado.get();
        registro.setUsadoEn(LocalDateTime.now());
        tokenRepository.save(registro);

        cerrarSesionesDe(usuario.getEmail());

        log.info("Contraseña restablecida para {}", usuario.getEmail());
        return List.of();
    }

    /**
     * Echa de la aplicación todas las sesiones abiertas con esa cuenta y borra sus
     * recordatorios de "mantener sesión activa".
     */
    private void cerrarSesionesDe(String email) {
        try {
            sesiones.findByPrincipalName(email).keySet().forEach(sesiones::deleteById);
            recordatorios.removeUserTokens(email);
        } catch (RuntimeException e) {
            // El cambio de contraseña ya está guardado; no se deshace por esto.
            log.error("No se pudieron cerrar las sesiones de {}: {}", email, e.getMessage(), e);
        }
    }
}
