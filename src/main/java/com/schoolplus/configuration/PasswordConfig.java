package com.schoolplus.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Cifrado de contraseñas. Decisión D-008.
 *
 * <p>Origen: un proyecto de referencia (configuration/PasswordConfig.java), que devolvía
 * {@code new BCryptPasswordEncoder()} — BCrypt {@code $2a$}, coste 10. Los tres repos de
 * referencia usan exactamente ese, así que <strong>los hashes heredados se migran tal cual
 * y nadie tiene que cambiar su clave</strong> (docs/DECISIONES.md).</p>
 *
 * <p>Lo que cambia aquí: en vez del codificador desnudo se usa un
 * {@link DelegatingPasswordEncoder}. Sigue leyendo los {@code $2a$10$} de siempre, pero
 * cifra lo nuevo con coste 12 y deja la puerta abierta a cambiar de algoritmo sin invalidar
 * lo ya guardado.</p>
 *
 * <p><strong>Ojo con el formato.</strong> El delegating antepone un prefijo
 * {@code {bcrypt}} a lo que cifra y lo espera al descifrar. Los hashes heredados no lo
 * traen, por eso se declara BCrypt como codificador "para lo que llegue sin prefijo".</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${app.security.bcrypt-strength:12}") int fuerza) {

        String idPorDefecto = "bcrypt";
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(fuerza);

        DelegatingPasswordEncoder encoder =
                new DelegatingPasswordEncoder(idPorDefecto, Map.of(idPorDefecto, bcrypt));

        // Sin esta línea, un hash heredado sin prefijo se rechazaría con
        // "There is no PasswordEncoder mapped for the id null" y la migración de usuarios
        // obligaría a que todo el mundo restableciera su contraseña.
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);

        return encoder;
    }
}
