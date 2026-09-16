package com.schoolplus.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * El reloj del sistema como bean.
 *
 * <p>Existe por la asistencia: "presente o tarde", "el QR venció" y "la sesión lleva
 * demasiado abierta" son reglas que dependen de la hora, y un {@code LocalDateTime.now()}
 * suelto dentro del servicio no se puede probar sin esperar en el test. Inyectando el
 * {@link Clock}, los tests fijan la hora con {@code Clock.fixed(...)}.</p>
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }
}
