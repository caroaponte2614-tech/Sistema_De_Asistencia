package com.schoolplus.services.password;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.PasswordResetToken;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.PasswordResetTokenRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auth.ValidationService;
import com.schoolplus.services.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las tres correcciones sobre el original de un proyecto de referencia: respuesta neutra, token de un
 * solo uso y cierre de sesiones al cambiar la contraseña (docs/DECISIONES.md).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final int MINUTOS = 30;

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private PersistentTokenRepository recordatorios;
    @Mock
    private FindByIndexNameSessionRepository<Session> sesiones;

    private PasswordResetService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new PasswordResetService(
                tokenRepository, usuarioRepository, passwordEncoder,
                new ValidationService(10), emailService, recordatorios, sesiones, MINUTOS);
    }

    @Test
    void noEnviaNadaNiRevelaNadaSiElCorreoNoTieneCuenta() {
        when(usuarioRepository.findByEmail("nadie@schoolplus.example")).thenReturn(Optional.empty());

        Optional<String> enlace = servicio.solicitar("nadie@schoolplus.example");

        assertThat(enlace).isEmpty();
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacionPassword(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void tampocoEnviaNadaSiLaCuentaEstaInactiva() {
        Usuario inactivo = usuario();
        inactivo.setEstado(EstadoUsuario.INACTIVO);
        when(usuarioRepository.findByEmail(inactivo.getEmail())).thenReturn(Optional.of(inactivo));

        assertThat(servicio.solicitar(inactivo.getEmail())).isEmpty();
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void generaUnTokenDe30MinutosEInvalidaLosAnteriores() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(emailService.estaHabilitado()).thenReturn(true);

        servicio.solicitar("  ANA.GOMEZ@SCHOOLPLUS.EXAMPLE  ");

        verify(tokenRepository).invalidarVigentesDe(usuario.getId());

        ArgumentCaptor<PasswordResetToken> guardado = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(guardado.capture());

        PasswordResetToken token = guardado.getValue();
        assertThat(token.getUsuario()).isSameAs(usuario);
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getExpiraEn())
                .isAfter(LocalDateTime.now().plusMinutes(MINUTOS - 1))
                .isBefore(LocalDateTime.now().plusMinutes(MINUTOS + 1));

        verify(emailService).enviarRecuperacionPassword(
                usuario.getEmail(), usuario.getNombre(), token.getToken(), MINUTOS);
    }

    @Test
    void unTokenYaUsadoNoSeCanjeaDosVeces() {
        PasswordResetToken usado = token(LocalDateTime.now().plusMinutes(10));
        usado.setUsadoEn(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.buscarConUsuarioPorToken("t")).thenReturn(Optional.of(usado));

        assertThat(servicio.tokenCanjeable("t")).isFalse();
        assertThat(servicio.restablecer("t", "Alameda2026", "Alameda2026"))
                .anyMatch(error -> error.contains("no es válido"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unTokenVencidoNoSirve() {
        when(tokenRepository.buscarConUsuarioPorToken("t"))
                .thenReturn(Optional.of(token(LocalDateTime.now().minusMinutes(1))));

        assertThat(servicio.tokenCanjeable("t")).isFalse();
        assertThat(servicio.restablecer("t", "Alameda2026", "Alameda2026")).isNotEmpty();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void laContrasenaNuevaTambienPasaPorLaPolitica() {
        when(tokenRepository.buscarConUsuarioPorToken("t"))
                .thenReturn(Optional.of(token(LocalDateTime.now().plusMinutes(10))));

        assertThat(servicio.restablecer("t", "corta1", "corta1"))
                .anyMatch(error -> error.contains("al menos 10 caracteres"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void alCambiarLaContrasenaSeMarcaElTokenYSeCierranLasDemasSesiones() {
        PasswordResetToken vigente = token(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.buscarConUsuarioPorToken("t")).thenReturn(Optional.of(vigente));
        when(passwordEncoder.encode("Alameda2026")).thenReturn("{bcrypt}$2a$12$hash");
        Map<String, Session> abiertas = new HashMap<>();
        abiertas.put("sesion-vieja", null);
        lenient().when(sesiones.findByPrincipalName(anyString())).thenReturn(abiertas);

        List<String> errores = servicio.restablecer("t", "Alameda2026", "Alameda2026");

        assertThat(errores).isEmpty();
        assertThat(vigente.getUsadoEn()).isNotNull();
        assertThat(vigente.getUsuario().getPasswordHash()).isEqualTo("{bcrypt}$2a$12$hash");
        verify(usuarioRepository).save(vigente.getUsuario());
        verify(sesiones).deleteById("sesion-vieja");
        verify(recordatorios).removeUserTokens("ana.gomez@schoolplus.example");
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId("11111111-1111-1111-1111-111111111111");
        usuario.setNombre("Ana Gómez");
        usuario.setEmail("ana.gomez@schoolplus.example");
        usuario.setPasswordHash("$2a$10$heredado");
        usuario.setEstado(EstadoUsuario.ACTIVO);
        return usuario;
    }

    private PasswordResetToken token(LocalDateTime expiraEn) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId("22222222-2222-2222-2222-222222222222");
        token.setToken("t");
        token.setUsuario(usuario());
        token.setExpiraEn(expiraEn);
        return token;
    }
}
