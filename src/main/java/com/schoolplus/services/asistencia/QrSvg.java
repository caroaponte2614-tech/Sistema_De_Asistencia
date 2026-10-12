package com.schoolplus.services.asistencia;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Convierte una URL en un QR listo para poner en un {@code <img>}.
 *
 * <p>Se dibuja como SVG y no como PNG por dos motivos: en un proyector el vector no se ve
 * pixelado por grande que se ponga, y así basta con {@code zxing:core} —el módulo
 * {@code javase}, que trae el conversor a imagen, no hace falta.</p>
 *
 * <p>Sale como {@code data:} URI porque la política de contenido del sitio permite
 * {@code img-src 'self' data:}, y así la imagen no necesita otra petición al servidor
 * cada 15 segundos.</p>
 */
public final class QrSvg {

    private static final int MARGEN_MODULOS = 2;

    private QrSvg() {
    }

    public static String dataUri(String contenido) {
        String svg = svg(contenido);
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    static String svg(String contenido) {
        BitMatrix matriz = matriz(contenido);
        int ancho = matriz.getWidth();
        int alto = matriz.getHeight();

        StringBuilder trazo = new StringBuilder();
        for (int y = 0; y < alto; y++) {
            int x = 0;
            while (x < ancho) {
                if (!matriz.get(x, y)) {
                    x++;
                    continue;
                }
                // Se agrupan los módulos negros seguidos en un solo rectángulo: el SVG de
                // un QR pasa de miles de figuras a unos cientos.
                int inicio = x;
                while (x < ancho && matriz.get(x, y)) {
                    x++;
                }
                trazo.append("M").append(inicio).append(" ").append(y)
                        .append("h").append(x - inicio).append("v1h-").append(x - inicio).append("z");
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + ancho + " " + alto + "\" "
                + "shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Código QR de asistencia\">"
                + "<rect width=\"" + ancho + "\" height=\"" + alto + "\" fill=\"#ffffff\"/>"
                + "<path d=\"" + trazo + "\" fill=\"#0f172a\"/>"
                + "</svg>";
    }

    private static BitMatrix matriz(String contenido) {
        try {
            // Corrección de errores M: aguanta suciedad y reflejos del proyector sin
            // engordar el código como lo haría H.
            return new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, MARGEN_MODULOS,
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR de asistencia.", e);
        }
    }
}
