package com.schoolplus.services.auth;

import com.schoolplus.model.EventoAcceso;
import com.schoolplus.repository.AuditoriaAccesoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Límite de intentos (§4.1). Pieza nueva: ninguno de los tres repos de referencia limitaba
 * nada — hallazgo #3 de docs/DECISIONES.md
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceTest {

    @Mock
    private AuditoriaAccesoRepository auditoria;

    private RateLimitService rateLimit;

    @BeforeEach
    void prepararServicio() {
        // 5 por cuenta / 20 por IP en 15 min; 3 por cuenta / 10 por IP en 60 min.
        rateLimit = new RateLimitService(auditoria, 5, 20, 15, 3, 10, 60);
    }

    @Test
    void dejaPasarMientrasHayaCupo() {
        contarPorCuenta(EventoAcceso.LOGIN_FALLIDO, 4);
        contarPorIp(EventoAcceso.LOGIN_FALLIDO, 7);

        assertThat(rateLimit.loginBloqueado("ana@schoolplus.example", "10.0.0.9")).isFalse();
    }

    @Test
    void bloqueaLaCuentaAlQuintoFallo() {
        contarPorCuenta(EventoAcceso.LOGIN_FALLIDO, 5);
        contarPorIp(EventoAcceso.LOGIN_FALLIDO, 0);

        assertThat(rateLimit.loginBloqueado("ana@schoolplus.example", "10.0.0.9")).isTrue();
    }

    @Test
    void bloqueaLaIpAunqueCadaCuentaVayaSuelta() {
        contarPorCuenta(EventoAcceso.LOGIN_FALLIDO, 1);
        contarPorIp(EventoAcceso.LOGIN_FALLIDO, 20);

        assertThat(rateLimit.loginBloqueado("otra@schoolplus.example", "10.0.0.9")).isTrue();
    }

    @Test
    void laRecuperacionTieneSuPropioCupo() {
        contarPorCuenta(EventoAcceso.RESET_SOLICITADO, 3);
        contarPorIp(EventoAcceso.RESET_SOLICITADO, 0);

        assertThat(rateLimit.recuperacionBloqueada("ana@schoolplus.example", "10.0.0.9")).isTrue();
        assertThat(rateLimit.loginBloqueado("ana@schoolplus.example", "10.0.0.9")).isFalse();
    }

    @Test
    void elCorreoSeNormalizaAntesDeContar() {
        when(auditoria.countByEmailIntentadoAndEventoAndOcurridoEnAfter(
                eq("ana@schoolplus.example"), eq(EventoAcceso.LOGIN_FALLIDO), any(LocalDateTime.class)))
                .thenReturn(5L);

        assertThat(rateLimit.loginBloqueado("  ANA@SCHOOLPLUS.EXAMPLE ", null)).isTrue();
    }

    @Test
    void siLaBitacoraFallaSeDejaPasarEnVezDeDejarATodosFuera() {
        when(auditoria.countByEmailIntentadoAndEventoAndOcurridoEnAfter(
                any(), any(), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("base caída"));

        assertThat(rateLimit.loginBloqueado("ana@schoolplus.example", "10.0.0.9")).isFalse();
    }

    private void contarPorCuenta(EventoAcceso evento, long veces) {
        when(auditoria.countByEmailIntentadoAndEventoAndOcurridoEnAfter(
                any(), eq(evento), any(LocalDateTime.class))).thenReturn(veces);
    }

    private void contarPorIp(EventoAcceso evento, long veces) {
        when(auditoria.countByIpAndEventoAndOcurridoEnAfter(
                any(), eq(evento), any(LocalDateTime.class))).thenReturn(veces);
    }
}
