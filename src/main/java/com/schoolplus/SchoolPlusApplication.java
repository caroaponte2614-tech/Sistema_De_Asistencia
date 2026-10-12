package com.schoolplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Plataforma escolar School_Plus.
 *
 * <p>{@code @EnableScheduling}: lo que dispara a {@code CierreAutomaticoJob}, el cierre
 * automático de las sesiones de asistencia olvidadas.</p>
 */
@SpringBootApplication
@EnableScheduling
public class SchoolPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolPlusApplication.class, args);
    }
}
