package com.schoolplus.services.documentos;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué archivos se aceptan y con qué clave quedan guardados. */
class PoliticaArchivosTest {

    private static final byte[] PDF = "%PDF-1.7 contenido".getBytes();

    @Test
    void generaUnaClaveConCarpetaDelTipoUuidYExtensionSinElNombreOriginal() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "Mi Hoja de Vida.PDF", "text/plain", PDF);

        PoliticaArchivos.ArchivoValidado validado = PoliticaArchivos.validar(archivo, TipoArchivo.DOCUMENTO);

        assertThat(validado.clave()).matches("documentos/[0-9a-f-]{36}\\.pdf");
        // El MIME que declaró el navegador se ignora: manda el canónico del formato.
        assertThat(validado.mime()).isEqualTo("application/pdf");
    }

    @Test
    void dosSubidasDelMismoArchivoNoChocan() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "a.pdf", "application/pdf", PDF);

        assertThat(PoliticaArchivos.validar(archivo, TipoArchivo.SOPORTE).clave())
                .isNotEqualTo(PoliticaArchivos.validar(archivo, TipoArchivo.SOPORTE).clave());
    }

    @Test
    void rechazaUnaExtensionQueElTipoNoAdmite() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "script.html", "text/html", "<script>".getBytes());

        assertThatThrownBy(() -> PoliticaArchivos.validar(archivo, TipoArchivo.DOCUMENTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    void rechazaUnFormatoValidoEnOtroTipo() {
        MockMultipartFile archivo = new MockMultipartFile("miniatura", "video.mp4", "video/mp4", new byte[]{1});

        assertThatThrownBy(() -> PoliticaArchivos.validar(archivo, TipoArchivo.IMAGEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnContenidoQueNoCorrespondeASuExtension() {
        MockMultipartFile disfrazado = new MockMultipartFile("archivo", "foto.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> PoliticaArchivos.validar(disfrazado, TipoArchivo.IMAGEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void rechazaUnArchivoMasGrandeQueElMaximoDelTipo() {
        byte[] grande = new byte[(int) TipoArchivo.IMAGEN.tamanoMaximoBytes() + 1];
        grande[0] = (byte) 0x89;
        grande[1] = 'P';
        grande[2] = 'N';
        grande[3] = 'G';
        MockMultipartFile archivo = new MockMultipartFile("miniatura", "grande.png", "image/png", grande);

        assertThatThrownBy(() -> PoliticaArchivos.validar(archivo, TipoArchivo.IMAGEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamaño máximo");
    }

    @Test
    void rechazaUnArchivoVacioOAusente() {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> PoliticaArchivos.validar(vacio, TipoArchivo.DOCUMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoliticaArchivos.validar(null, TipoArchivo.DOCUMENTO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nombreParaMostrarQuitaLasCarpetas() {
        assertThat(PoliticaArchivos.nombreParaMostrar("C:\\fakepath\\..\\excusa.pdf")).isEqualTo("excusa.pdf");
        assertThat(PoliticaArchivos.nombreParaMostrar("../../etc/passwd")).isEqualTo("passwd");
        assertThat(PoliticaArchivos.nombreParaMostrar(null)).isEqualTo("archivo");
    }
}
