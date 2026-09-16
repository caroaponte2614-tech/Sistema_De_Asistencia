package com.schoolplus.configuration.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * La cookie que identifica el celular de un estudiante para la asistencia.
 *
 * <p>La pone el servidor con un valor aleatorio de 256 bits y la marca {@code HttpOnly}: el
 * JavaScript de la página no puede leerla, así que no se puede copiar y pegar en el
 * teléfono de un compañero desde la consola del navegador ni exfiltrar con un XSS. En la
 * base solo se guarda su SHA-256 (ver {@code DispositivoService}).</p>
 *
 * <p>Se elige una cookie y no {@code localStorage} justamente por eso, y porque Safari
 * limita a siete días lo que escribe el JavaScript, no lo que manda el servidor: una cookie
 * de sesión borrada obligaría a pedir autorización cada semana.</p>
 *
 * <p>Cambiar de navegador o borrar los datos del sitio equivale a cambiar de celular: el
 * registro se rechaza y queda pedida la autorización del profesor. Es el precio de que la
 * vinculación signifique algo.</p>
 */
@Component
public class CookieDispositivo {

    public static final String NOMBRE = "SP_DISPOSITIVO";

    private static final Duration VIGENCIA = Duration.ofDays(365 * 3L);
    private static final int BYTES = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** El identificador que trae la petición, o {@code null} si el navegador no lo mandó. */
    public String leer(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> NOMBRE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Devuelve el identificador del dispositivo, creándolo si esta es la primera visita.
     *
     * <p>Se llama al pintar las pantallas de asistencia y no al registrar: así la cookie ya
     * viaja en el POST que registra, sin depender de que el navegador la acepte a mitad de
     * una petición de fondo.</p>
     */
    public String asegurar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        String existente = leer(peticion);
        if (existente != null) {
            return existente;
        }
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ResponseCookie cookie = ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                // Secure solo si la petición ya viene por HTTPS: en desarrollo, sobre
                // http://localhost, una cookie Secure no se guardaría y nada funcionaría.
                .secure(peticion.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(VIGENCIA)
                .build();
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return valor;
    }
}
