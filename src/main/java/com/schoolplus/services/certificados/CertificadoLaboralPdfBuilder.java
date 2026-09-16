package com.schoolplus.services.certificados;

import com.schoolplus.model.Usuario;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el PDF del certificado laboral: dibujado a mano con PDFBox, no HTML-a-PDF —
 * alcanza para una hoja con texto fijo. El QR de la esquina se genera con ZXing
 * ({@code core}, no {@code javase}): convertir el {@link BitMatrix} a imagen son
 * ~15 líneas, no vale la pena la dependencia completa por
 * {@code MatrixToImageWriter}.
 */
@Component
public class CertificadoLaboralPdfBuilder {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGEN_IZQUIERDO = 60;
    private static final float ANCHO_TEXTO = 470;

    public byte[] generar(Usuario usuario, String codigoVerificacion, String urlVerificacion) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);

            PDFont fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            BufferedImage qr = generarQr(urlVerificacion, 120);
            PDImageXObject imagenQr = LosslessFactory.createFromImage(documento, qr);

            try (PDPageContentStream cs = new PDPageContentStream(documento, pagina)) {
                float y = 740;

                cs.beginText();
                cs.setFont(fuenteTitulo, 18);
                cs.newLineAtOffset(MARGEN_IZQUIERDO, y);
                cs.showText("CERTIFICADO LABORAL");
                cs.endText();
                y -= 40;

                String cargo = usuario.getCargo() == null ? "—" : usuario.getCargo().getNombre();
                String fechaIngreso = usuario.getFechaIngreso() == null ? "—" : usuario.getFechaIngreso().format(FORMATO_FECHA);
                String cuerpo = "School_Plus hace constar que " + usuario.getNombre()
                        + ", identificado(a) con cédula de ciudadanía número " + usuario.getCedula()
                        + ", labora actualmente en la institución desempeñando el cargo de " + cargo
                        + ", desde el " + fechaIngreso + " a la fecha.";

                y = escribirParrafo(cs, fuenteTexto, 11, cuerpo, MARGEN_IZQUIERDO, y, ANCHO_TEXTO, 16);
                y -= 20;

                y = escribirParrafo(cs, fuenteTexto, 11,
                        "Este documento se expide a solicitud del interesado, a los " + LocalDate.now().format(FORMATO_FECHA) + ".",
                        MARGEN_IZQUIERDO, y, ANCHO_TEXTO, 16);
                y -= 40;

                cs.beginText();
                cs.setFont(fuenteTexto, 9);
                cs.newLineAtOffset(MARGEN_IZQUIERDO, y);
                cs.showText("Código de verificación: " + codigoVerificacion);
                cs.endText();

                cs.drawImage(imagenQr, MARGEN_IZQUIERDO, y - 130, 100, 100);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el certificado laboral.", e);
        }
    }

    /** Parte el texto en líneas que entran en {@code ancho} puntos, a mano — PDFBox no envuelve texto. */
    private float escribirParrafo(PDPageContentStream cs, PDFont fuente, float tamano, String texto,
                                  float x, float y, float ancho, float interlineado) throws IOException {
        for (String linea : partirEnLineas(fuente, tamano, texto, ancho)) {
            cs.beginText();
            cs.setFont(fuente, tamano);
            cs.newLineAtOffset(x, y);
            cs.showText(linea);
            cs.endText();
            y -= interlineado;
        }
        return y;
    }

    private List<String> partirEnLineas(PDFont fuente, float tamano, String texto, float anchoMaximo) throws IOException {
        List<String> lineas = new java.util.ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String propuesta = actual.isEmpty() ? palabra : actual + " " + palabra;
            float ancho = fuente.getStringWidth(propuesta) / 1000 * tamano;
            if (ancho > anchoMaximo && !actual.isEmpty()) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(propuesta);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private BufferedImage generarQr(String contenido, int tamano) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano);
            BufferedImage imagen = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < tamano; x++) {
                for (int y = 0; y < tamano; y++) {
                    imagen.setRGB(x, y, matriz.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return imagen;
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR.", e);
        }
    }
}
