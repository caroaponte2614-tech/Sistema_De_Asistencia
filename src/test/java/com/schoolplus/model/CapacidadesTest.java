package com.schoolplus.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amarra las constantes de {@link Capacidades} al catálogo real de las migraciones.
 *
 * <p>Sin esto, un código mal escrito en un {@code @PreAuthorize} no rompe nada visible:
 * la capacidad simplemente no existe, la consulta no devuelve nada y el usuario recibe un
 * 403 que nadie sabe explicar. Aquí revienta el build en su lugar.</p>
 *
 * <p>Lee el SQL en vez de la base de datos a propósito: es un test unitario y tiene que
 * correr sin MySQL delante. El catálogo nació completo en V1, pero nada impide que una
 * migración posterior agregue una fila nueva a {@code capacidad} —como V11 con
 * {@code ORGANIGRAMA_ADMINISTRAR}—, así que se recorren todas las migraciones, no solo
 * la primera.</p>
 */
class CapacidadesTest {

    private static final Path CARPETA_MIGRACIONES = Path.of("src/main/resources/db/migration");

    @Test
    void cadaConstanteExisteEnElCatalogoDeLasMigraciones() throws IOException {
        Set<String> enLasMigraciones = codigosDeCapacidadEnLasMigraciones();

        assertThat(constantesDeclaradas())
                .isNotEmpty()
                .allSatisfy(codigo -> assertThat(enLasMigraciones)
                        .as("La constante %s no está en ningún INSERT INTO capacidad", codigo)
                        .contains(codigo));
    }

    @Test
    void cadaCapacidadDelCatalogoTieneSuConstante() throws IOException {
        assertThat(codigosDeCapacidadEnLasMigraciones())
                .as("Hay capacidades en las migraciones sin constante en Capacidades.java")
                .containsExactlyInAnyOrderElementsOf(constantesDeclaradas());
    }

    @Test
    void losCuatroRolesDelPromptEstanDeclarados() {
        assertThat(List.of(Rol.ADMIN, Rol.SUPERVISOR, Rol.COORDINADOR, Rol.USUARIO))
                .containsExactlyInAnyOrder("ADMIN", "SUPERVISOR", "COORDINADOR", "USUARIO");
    }

    @Test
    void losAmbitosVanDeMenorAMayorAlcance() {
        // CapacidadService se apoya en el orden del enum para quedarse con el alcance
        // mayor cuando dos roles conceden la misma capacidad.
        assertThat(Ambito.PROPIO.compareTo(Ambito.AREA)).isNegative();
        assertThat(Ambito.AREA.compareTo(Ambito.TODOS)).isNegative();
    }

    private List<String> constantesDeclaradas() {
        List<String> codigos = new ArrayList<>();
        for (Field campo : Capacidades.class.getDeclaredFields()) {
            if (Modifier.isStatic(campo.getModifiers()) && campo.getType() == String.class) {
                try {
                    codigos.add((String) campo.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return codigos;
    }

    /**
     * Extrae los códigos de todos los bloques {@code INSERT INTO capacidad (...) VALUES ...}
     * de todas las migraciones, en orden de versión.
     */
    private Set<String> codigosDeCapacidadEnLasMigraciones() throws IOException {
        Set<String> codigos = new java.util.LinkedHashSet<>();
        boolean huboAlMenosUnInsert = false;

        try (Stream<Path> archivos = Files.list(CARPETA_MIGRACIONES)) {
            List<Path> migraciones = archivos
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path migracion : migraciones) {
                String sql = Files.readString(migracion, StandardCharsets.UTF_8);
                int desde = 0;
                int inicio;
                while ((inicio = sql.indexOf("INSERT INTO capacidad", desde)) >= 0) {
                    huboAlMenosUnInsert = true;
                    String bloque = sql.substring(inicio, sql.indexOf(';', inicio));
                    Matcher m = Pattern.compile("\\('([A-Z_]+)'").matcher(bloque);
                    while (m.find()) {
                        codigos.add(m.group(1));
                    }
                    desde = sql.indexOf(';', inicio) + 1;
                }
            }
        }

        assertThat(huboAlMenosUnInsert).as("Ninguna migración trae el catálogo de capacidades").isTrue();
        return codigos.stream().collect(Collectors.toUnmodifiableSet());
    }
}
