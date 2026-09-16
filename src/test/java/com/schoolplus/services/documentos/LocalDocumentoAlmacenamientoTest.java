package com.schoolplus.services.documentos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El almacenamiento en disco: ida y vuelta, rangos de bytes y el candado contra path traversal. */
class LocalDocumentoAlmacenamientoTest {

    @TempDir
    Path carpeta;

    private LocalDocumentoAlmacenamiento almacenamiento;

    @BeforeEach
    void preparar() {
        almacenamiento = new LocalDocumentoAlmacenamiento(carpeta.toString());
    }

    @Test
    void subirYDescargarDevuelveLosMismosBytes() throws Exception {
        subir("documentos/abc.pdf", "%PDF-1.4 hola");

        try (InputStream flujo = almacenamiento.descargar("documentos/abc.pdf")) {
            assertThat(new String(flujo.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 hola");
        }
        assertThat(Files.exists(carpeta.resolve("documentos").resolve("abc.pdf"))).isTrue();
    }

    @Test
    void descargarUnRangoDevuelveSoloEsosBytes() throws Exception {
        subir("videos/v.mp4", "0123456789");

        try (InputStream flujo = almacenamiento.descargar("videos/v.mp4", 3L, 4L)) {
            assertThat(new String(flujo.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("3456");
        }
    }

    @Test
    void infoDevuelveTamanoYMimeCanonicoPorExtension() {
        subir("imagenes/foto.png", "12345");

        InfoObjeto info = almacenamiento.info("imagenes/foto.png");

        assertThat(info.tamanoBytes()).isEqualTo(5);
        assertThat(info.mime()).isEqualTo("image/png");
    }

    @Test
    void eliminarBorraElArchivo() {
        subir("soportes/x.pdf", "%PDF-");

        almacenamiento.eliminar("soportes/x.pdf");

        assertThat(Files.exists(carpeta.resolve("soportes").resolve("x.pdf"))).isFalse();
    }

    @Test
    void descargarUnArchivoInexistenteFallaConExcepcionDeAlmacenamiento() {
        assertThatThrownBy(() -> almacenamiento.descargar("documentos/no-existe.pdf"))
                .isInstanceOf(AlmacenamientoDocumentosException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../fuera.txt",
            "documentos/../../fuera.txt",
            "/etc/passwd",
            "C:/Windows/win.ini",
            "documentos\\..\\..\\fuera.txt",
            ".",
            " "
    })
    void rechazaClavesQueSalenDeLaRaiz(String clave) {
        assertThatThrownBy(() -> almacenamiento.subir(clave, entrada("x"), 1, "text/plain"))
                .isInstanceOf(AlmacenamientoDocumentosException.class);
        assertThatThrownBy(() -> almacenamiento.descargar(clave))
                .isInstanceOf(AlmacenamientoDocumentosException.class);
        assertThat(Files.exists(carpeta.getParent().resolve("fuera.txt"))).isFalse();
    }

    private void subir(String clave, String contenido) {
        byte[] bytes = contenido.getBytes(StandardCharsets.UTF_8);
        almacenamiento.subir(clave, new ByteArrayInputStream(bytes), bytes.length, null);
    }

    private static InputStream entrada(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
    }
}
