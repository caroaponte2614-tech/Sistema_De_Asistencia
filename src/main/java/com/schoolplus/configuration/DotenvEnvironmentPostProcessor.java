package com.schoolplus.configuration;

// Origen: un proyecto de referencia (configuration/DotenvEnvironmentPostProcessor.java)
// Se porta la lógica sin tocar. School_Plus ya traía .env.example y un README que dice
// "cp .env.example .env", pero nada leía ese archivo fuera de docker-compose: sin esta
// clase, arrancar con ./mvnw spring-boot:run obligaba a exportar cada variable a mano.
//
// Un cambio respecto al original: la interfaz se toma de org.springframework.boot y no
// de org.springframework.boot.env, que Spring Boot 4 dejó obsoleta y marcada para
// eliminación. El repo de origen sigue con la vieja y compila con aviso.

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga un archivo {@code .env} (formato {@code CLAVE=valor}) del directorio de
 * trabajo y lo expone como una fuente de propiedades de BAJA prioridad.
 *
 * <p>Objetivo: mantener secretos (contraseñas SMTP, claves) FUERA de git. El
 * {@code application.properties} solo declara placeholders {@code ${VAR:default}};
 * los valores reales viven en {@code .env}, que está en {@code .gitignore}.</p>
 *
 * <p>Precedencia: se agrega con {@code addLast}, de modo que una variable de
 * entorno REAL del sistema operativo (producción, docker-compose) siempre gana sobre
 * el {@code .env}. Si el archivo no existe, es un no-op silencioso.</p>
 *
 * <p>Registrado en {@code META-INF/spring.factories}. Corre muy temprano (antes del
 * logging), por eso reporta errores por {@code System.err}.</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = Paths.get(".env");
        if (!Files.exists(envFile)) {
            return;
        }
        try {
            Map<String, Object> props = parse(Files.readAllLines(envFile, StandardCharsets.UTF_8));
            if (!props.isEmpty()) {
                environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, props));
            }
        } catch (IOException ex) {
            System.err.println("[dotenv] No se pudo leer .env: " + ex.getMessage());
        }
    }

    /**
     * Parsea líneas {@code CLAVE=valor}. Ignora líneas vacías y comentarios ({@code #}).
     * El valor conserva espacios internos (p. ej. el App Password de Gmail, que viene
     * en grupos de cuatro); solo se recortan extremos y comillas envolventes.
     */
    static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String line : lines) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = s.substring(0, eq).strip();
            String value = unquote(s.substring(eq + 1).strip());
            if (!key.isEmpty()) {
                props.put(key, value);
            }
        }
        return props;
    }

    /** Quita comillas simples o dobles envolventes si las hay. */
    private static String unquote(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
