package com.schoolplus.services.asistencia;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El QR dinámico: qué token vale, cuál venció y cuál es falso.
 *
 * <p>La hora se fija con {@link Clock#fixed}: "esperar 15 segundos" en un test es esperar de
 * verdad, y aquí basta con fabricar el servicio con un reloj adelantado.</p>
 */
class TokenAsistenciaServiceTest {

    private static final String CLAVE = "clave-de-prueba-lo-bastante-larga-123456";
    private static final String SESION = "01a0a73c-512e-7e97-8e9d-6d0a11b27f1c";
    /** Múltiplo exacto de 15 para caer al inicio de una ventana. */
    private static final Instant T0 = Instant.ofEpochSecond(1_700_000_010L);

    private TokenAsistenciaService servicioEn(Instant momento) {
        return servicioEn(momento, CLAVE);
    }

    private TokenAsistenciaService servicioEn(Instant momento, String clave) {
        ReglasAsistencia reglas = new ReglasAsistencia(15, 1, 120, 10, 4, false, 0, 0, 300);
        return new TokenAsistenciaService(clave, reglas, Clock.fixed(momento, ZoneOffset.UTC));
    }

    @Test
    void elTokenReciénEmitidoVale() {
        TokenAsistenciaService servicio = servicioEn(T0);

        var verificacion = servicio.verificar(servicio.emitir(SESION));

        assertThat(verificacion.esValido()).isTrue();
        assertThat(verificacion.sesionId()).isEqualTo(SESION);
    }

    @Test
    void unTokenDeLaVentanaAnteriorTodavíaVale() {
        // Rotación de 15 s con una ventana de gracia: un QR sirve entre 15 y 30 segundos.
        String token = servicioEn(T0).emitir(SESION);

        var verificacion = servicioEn(T0.plus(Duration.ofSeconds(16))).verificar(token);

        assertThat(verificacion.esValido()).isTrue();
    }

    @Test
    void pasadaLaGraciaElTokenVence() {
        String token = servicioEn(T0).emitir(SESION);

        var verificacion = servicioEn(T0.plus(Duration.ofSeconds(31))).verificar(token);

        // Vencido, no inválido: la firma es buena, lo que pasó es que el QR ya cambió.
        assertThat(verificacion.estado()).isEqualTo(TokenAsistenciaService.Verificacion.Estado.VENCIDO);
        assertThat(verificacion.esValido()).isFalse();
    }

    @Test
    void unaFirmaAlteradaNoVale() {
        TokenAsistenciaService servicio = servicioEn(T0);
        String token = servicio.emitir(SESION);
        String manipulado = token.substring(0, token.length() - 3) + "AAA";

        assertThat(servicio.verificar(manipulado).estado())
                .isEqualTo(TokenAsistenciaService.Verificacion.Estado.INVALIDO);
    }

    @Test
    void cambiarLaSesiónDentroDelTokenNoVale() {
        TokenAsistenciaService servicio = servicioEn(T0);
        String[] partes = servicio.emitir(SESION).split("\\.");
        String deOtraSesion = partes[0] + ".otra-sesion." + partes[2] + "." + partes[3];

        assertThat(servicio.verificar(deOtraSesion).estado())
                .isEqualTo(TokenAsistenciaService.Verificacion.Estado.INVALIDO);
    }

    @Test
    void unTokenDeOtroColegioNoValeAquí() {
        String ajeno = servicioEn(T0, "otra-clave-distinta-de-la-nuestra-987").emitir(SESION);

        assertThat(servicioEn(T0).verificar(ajeno).estado())
                .isEqualTo(TokenAsistenciaService.Verificacion.Estado.INVALIDO);
    }

    @Test
    void laBasuraNoRevienta() {
        TokenAsistenciaService servicio = servicioEn(T0);

        assertThat(servicio.verificar(null).esValido()).isFalse();
        assertThat(servicio.verificar("").esValido()).isFalse();
        assertThat(servicio.verificar("cualquier-cosa").esValido()).isFalse();
        assertThat(servicio.verificar("v1.sesion.no-es-numero.firma").esValido()).isFalse();
    }

    // ── Código de 6 dígitos ──────────────────────────────────────────────────

    @Test
    void elCódigoTieneSeisDígitosYEsEstableDentroDeLaVentana() {
        String codigo = servicioEn(T0).codigoDe(SESION);

        assertThat(codigo).hasSize(6).containsOnlyDigits();
        assertThat(servicioEn(T0.plus(Duration.ofSeconds(5))).codigoDe(SESION)).isEqualTo(codigo);
    }

    @Test
    void elCódigoCambiaConLaVentanaYAceptaElAnterior() {
        String deLaPrimeraVentana = servicioEn(T0).codigoDe(SESION);
        TokenAsistenciaService siguiente = servicioEn(T0.plus(Duration.ofSeconds(16)));

        assertThat(siguiente.codigoDe(SESION)).isNotEqualTo(deLaPrimeraVentana);
        // El de la ventana anterior sigue sirviendo: da tiempo a teclearlo.
        assertThat(siguiente.codigoCoincide(SESION, deLaPrimeraVentana)).isTrue();
        // Dos ventanas después, ya no.
        assertThat(servicioEn(T0.plus(Duration.ofSeconds(31))).codigoCoincide(SESION, deLaPrimeraVentana)).isFalse();
    }

    @Test
    void elCódigoDeOtraSesiónNoCoincide() {
        TokenAsistenciaService servicio = servicioEn(T0);
        String codigoDeOtra = servicio.codigoDe("01a0a73c-0000-7000-8000-000000000000");

        assertThat(servicio.codigoCoincide(SESION, codigoDeOtra)).isFalse();
        assertThat(servicio.codigoCoincide(SESION, "12345")).isFalse();
        assertThat(servicio.codigoCoincide(SESION, null)).isFalse();
    }

    @Test
    void elCódigoSeLeeAunqueVengaConEspacios() {
        TokenAsistenciaService servicio = servicioEn(T0);
        String codigo = servicio.codigoDe(SESION);

        assertThat(servicio.codigoCoincide(SESION, codigo.substring(0, 3) + " " + codigo.substring(3))).isTrue();
    }
}
