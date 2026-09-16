package com.schoolplus.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La "regla dura" del prompt (§2.2): el hashing de School_Plus debe ser compatible con el del
 * sistema del que salen los usuarios, o la migración obliga a que todo el mundo cambie su
 * contraseña.
 *
 * <p>Los tres repos de referencia usan {@code new BCryptPasswordEncoder()} → {@code $2a$},
 * coste 10. Este test prueba que un hash de ese formato sigue validando aquí.</p>
 */
class PasswordConfigTest {

    // Coste 4 para que el test no tarde: el formato del hash es idéntico, que es lo que se
    // está comprobando.
    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder(4);

    @Test
    void validaUnHashHeredadoDeLosSistemasDeOrigen() {
        String heredado = new BCryptPasswordEncoder().encode("ClaveDeSiempre1");

        assertThat(heredado).startsWith("$2a$10$");
        assertThat(encoder.matches("ClaveDeSiempre1", heredado)).isTrue();
        assertThat(encoder.matches("otraCosa", heredado)).isFalse();
    }

    @Test
    void loNuevoSeCifraConPrefijoDeAlgoritmo() {
        String nuevo = encoder.encode("ClaveNueva2026");

        assertThat(nuevo).startsWith("{bcrypt}$2a$");
        assertThat(encoder.matches("ClaveNueva2026", nuevo)).isTrue();
    }
}
