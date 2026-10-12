package com.schoolplus.services.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Envío de correo transaccional.
 *
 * <p>Origen: un proyecto de referencia (services/email/EmailService.java). Se porta entera la
 * maquinaria de envío —bandera {@code app.mail.enabled}, validación del destinatario y del
 * remitente, y reintentos con espera creciente— porque ya está probada en producción. Lo
 * único que cambia son las dos plantillas, escritas con la paleta del login
 * (los prototipos de diseño (login)) para que el correo y la pantalla se parezcan.</p>
 *
 * <p>Nada de credenciales en el código: usuario y contraseña salen de variables de entorno
 * (hallazgo #1 de docs/DECISIONES.md).</p>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from-address:}")
    private String configuredFromAddress;

    @Value("${app.mail.from-name:School_Plus}")
    private String fromName;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.max-attempts:3}")
    private int maxAttempts;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean estaHabilitado() {
        return mailEnabled;
    }

    // ── Enlaces ──────────────────────────────────────────────────────────────

    public String enlaceVerificacion(String token) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/verificar")
                .queryParam("token", token)
                .toUriString();
    }

    public String enlaceRestablecer(String token) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/restablecer")
                .queryParam("token", token)
                .toUriString();
    }

    // ── Plantillas ───────────────────────────────────────────────────────────

    public EmailSendResult enviarVerificacionCuenta(String para, String nombre, String token) {
        String contenido = plantilla(
                "Verifica tu cuenta",
                escape(nombre),
                "Creaste una cuenta en School_Plus, la plataforma escolar. "
                        + "Confirma tu correo para activarla.",
                "Verificar mi cuenta",
                enlaceVerificacion(token),
                "El enlace vence en 24 horas. Si no fuiste tú, ignora este mensaje: "
                        + "la cuenta no se activará.");

        return enviarCorreo(para, "Verifica tu cuenta — School_Plus", contenido);
    }

    public EmailSendResult enviarRecuperacionPassword(String para, String nombre, String token,
                                                      int minutosVigencia) {
        String contenido = plantilla(
                "Recupera tu acceso",
                escape(nombre),
                "Pediste crear una contraseña nueva para tu cuenta de School_Plus.",
                "Crear contraseña nueva",
                enlaceRestablecer(token),
                "El enlace vence en " + minutosVigencia + " minutos y sirve una sola vez. "
                        + "Si no fuiste tú, ignora este mensaje: tu contraseña actual sigue funcionando.");

        return enviarCorreo(para, "Recuperación de contraseña — School_Plus", contenido);
    }

    /**
     * Lienzo común de los correos. Estilos en línea y tabla de ancho fijo: es lo único que
     * los clientes de correo pintan igual (Outlook ignora la mitad de CSS moderno).
     * Los colores son copia literal de los tokens de {@code styles.css}: un cliente de
     * correo no lee variables CSS, así que si cambia la paleta hay que cambiarlos aquí.
     */
    private String plantilla(String antetitulo, String nombre, String cuerpo,
                             String textoBoton, String enlace, String nota) {
        return """
                <html>
                  <body style="margin:0;padding:40px 16px;background-color:#eff6ff;font-family:Archivo,Helvetica,Arial,sans-serif;color:#0f172a;">
                    <div style="max-width:600px;margin:0 auto;background-color:#ffffff;border:2px solid #0f172a;">
                      <div style="background-color:#dbeafe;padding:24px 32px;border-bottom:2px solid #0f172a;">
                        <span style="display:inline-block;width:12px;height:12px;background-color:#2563eb;"></span>
                        <span style="font-size:14px;font-weight:800;letter-spacing:0.16em;text-transform:uppercase;color:#1e3a8a;margin-left:10px;">School_Plus</span>
                      </div>
                      <div style="padding:40px 32px;">
                        <p style="margin:0 0 14px;font-size:11px;letter-spacing:0.14em;text-transform:uppercase;color:#1e40af;">%s</p>
                        <h1 style="margin:0 0 20px;font-size:28px;line-height:1.15;color:#0f172a;">Hola, %s</h1>
                        <p style="margin:0 0 28px;font-size:15px;line-height:1.6;color:#334155;">%s</p>
                        <a href="%s" style="display:inline-block;background-color:#2563eb;color:#ffffff;font-size:14px;font-weight:800;letter-spacing:0.06em;text-transform:uppercase;text-decoration:none;padding:16px 22px;">%s</a>
                        <p style="margin:28px 0 0;font-size:13px;line-height:1.6;color:#475569;">%s</p>
                        <p style="margin:20px 0 0;font-size:12px;line-height:1.6;color:#64748b;word-break:break-all;">Si el botón no funciona, copia esta dirección en tu navegador:<br>%s</p>
                      </div>
                      <div style="padding:18px 32px;border-top:2px solid #0f172a;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;color:#475569;">
                        School_Plus · Plataforma escolar
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                escape(antetitulo), nombre, escape(cuerpo),
                enlace, escape(textoBoton), escape(nota), enlace);
    }

    // ── Maquinaria de envío (portada de un proyecto de referencia) ─────────────────────

    EmailSendResult enviarCorreo(String correo, String asunto, String contenido) {
        if (!mailEnabled) {
            log.warn("Envío de correo deshabilitado por app.mail.enabled=false. Destinatario omitido: {}", correo);
            return EmailSendResult.failed("El envío de correo está deshabilitado por configuración.");
        }

        String errorValidacion = validarPeticion(correo);
        if (errorValidacion != null) {
            log.warn("Correo no enviado a {}: {}", correo, errorValidacion);
            return EmailSendResult.failed(errorValidacion);
        }

        int intentos = Math.max(1, maxAttempts);
        Exception ultimoError = null;

        for (int intento = 1; intento <= intentos; intento++) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper =
                        new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                helper.setTo(correo.trim());
                helper.setFrom(new InternetAddress(
                        remitente(), fromName, StandardCharsets.UTF_8.name()));
                helper.setSubject(asunto);
                helper.setText(contenido, true);
                mailSender.send(mimeMessage);

                log.info("Correo enviado a {} con asunto '{}' en intento {}/{}",
                        correo, asunto, intento, intentos);
                return EmailSendResult.success();
            } catch (MessagingException | MailException | UnsupportedEncodingException e) {
                ultimoError = e;
                log.warn("Fallo enviando correo a {} en intento {}/{}: {}",
                        correo, intento, intentos, e.getMessage());
                if (intento < intentos) {
                    esperarAntesDeReintentar(intento);
                }
            }
        }

        String mensaje = ultimoError != null
                ? "No se pudo enviar el correo: " + ultimoError.getMessage()
                : "No se pudo enviar el correo por una causa desconocida.";
        log.error("Envío de correo fallido definitivamente para {}: {}", correo, mensaje, ultimoError);
        return EmailSendResult.failed(mensaje);
    }

    private String validarPeticion(String correo) {
        if (correo == null || correo.isBlank()) {
            return "El destinatario no tiene correo configurado.";
        }
        if (!EMAIL_PATTERN.matcher(correo.trim()).matches()) {
            return "El correo del destinatario no es válido.";
        }
        if (remitente().isBlank()) {
            return "No hay remitente SMTP configurado. Define MAIL_USERNAME o app.mail.from-address.";
        }
        if (mailPassword == null || mailPassword.isBlank()) {
            return "No hay contraseña SMTP configurada. Define MAIL_PASSWORD.";
        }
        return null;
    }

    private String remitente() {
        if (configuredFromAddress != null && !configuredFromAddress.isBlank()) {
            return configuredFromAddress.trim();
        }
        return mailUsername == null ? "" : mailUsername.trim();
    }

    private void esperarAntesDeReintentar(int intento) {
        try {
            Thread.sleep(500L * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String escape(String valor) {
        return valor == null ? "" : HtmlUtils.htmlEscape(valor);
    }
}
