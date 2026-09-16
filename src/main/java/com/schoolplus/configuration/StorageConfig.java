package com.schoolplus.configuration;

import com.schoolplus.services.documentos.LocalDocumentoAlmacenamiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;

/**
 * Deja lista la carpeta del almacenamiento local de archivos al arrancar (ver
 * {@link LocalDocumentoAlmacenamiento}). Se crea una vez aquí y no en cada subida, y el
 * log dice la ruta absoluta para que nadie tenga que adivinar dónde quedaron los archivos.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public ApplicationRunner asegurarCarpetaDeArchivos(LocalDocumentoAlmacenamiento almacenamiento) {
        return (ApplicationArguments args) -> {
            Files.createDirectories(almacenamiento.raiz());
            log.info("Almacenamiento local de archivos en {}", almacenamiento.raiz());
        };
    }
}
