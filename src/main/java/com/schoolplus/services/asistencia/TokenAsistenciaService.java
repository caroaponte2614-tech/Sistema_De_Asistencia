package com.schoolplus.services.asistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

/**
 * El QR dinámico y el código de 6 dígitos.
 *
 * <p>Ninguno de los dos se guarda: los dos se <em>derivan</em> del id de la sesión y de la
 * ventana de tiempo actual con un HMAC-SHA256. Eso significa que no hay tabla de tokens que
 * limpiar, que dos servidores calculan lo mismo, y sobre todo que un token viejo no se
 * puede "revivir": la ventana en la que se firmó ya pasó.</p>
 *
 * <p>Formato del token: {@code v1.<sesionId>.<ventana>.<firma>}. El id de la sesión viaja a
 * la vista porque no es un secreto —lo que no se puede fabricar es la firma—, y así el
 * servidor sabe de qué sesión hablar sin buscar a ciegas.</p>
 *
 * <p>Una foto del QR reenviada a un compañero ausente sirve, como mucho, los segundos que
 * quedan de la ventana ({@link ReglasAsistencia#rotacionSegundos()} más la gracia). Lo que
 * cierra el hueco de verdad es la vinculación del celular, no el token.</p>
 */
@Service
public class TokenAsistenciaService {

    private static final Logger log = LoggerFactory.getLogger(TokenAsistenciaService.class);

    private static final String ALGORITMO = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final int DIGITOS_CODIGO = 6;

    private final SecretKeySpec clave;
    private final ReglasAsistencia reglas;
    private final Clock reloj;

    public TokenAsistenciaService(@Value("${app.asistencia.token-key:}") String claveConfigurada,
                                  ReglasAsistencia reglas,
                                  Clock reloj) {
        this.reglas = reglas;
        this.reloj = reloj;
        this.clave = new SecretKeySpec(bytesDeLaClave(claveConfigurada), ALGORITMO);
    }

    /** El número de ventana actual: el tiempo partido en bloques del tamaño de la rotación. */
    public long ventanaActual() {
        return reloj.instant().getEpochSecond() / reglas.rotacionSegundos();
    }

    /** Cuántos segundos faltan para que el QR y el código cambien. */
    public int segundosParaRotar() {
        long transcurridos = reloj.instant().getEpochSecond() % reglas.rotacionSegundos();
        return (int) (reglas.rotacionSegundos() - transcurridos);
    }

    /** El token que va dentro del QR de esta ventana. */
    public String emitir(String sesionId) {
        long ventana = ventanaActual();
        return VERSION + "." + sesionId + "." + ventana + "." + firma("QR", sesionId, ventana);
    }

    /** El código de 6 dígitos de esta ventana. */
    public String codigoDe(String sesionId) {
        return codigoDe(sesionId, ventanaActual());
    }

    /**
     * Comprueba un token: de qué sesión es y si sigue vigente.
     *
     * <p>La firma se compara en tiempo constante ({@link MessageDigest#isEqual}) para no
     * filtrar, por lo que tarda la comparación, cuántos caracteres del intento eran
     * correctos.</p>
     */
    public Verificacion verificar(String token) {
        if (token == null || token.isBlank()) {
            return Verificacion.invalido();
        }
        String[] partes = token.split("\\.");
        if (partes.length != 4 || !VERSION.equals(partes[0]) || partes[1].isBlank()) {
            return Verificacion.invalido();
        }
        String sesionId = partes[1];
        long ventana;
        try {
            ventana = Long.parseLong(partes[2]);
        } catch (NumberFormatException e) {
            return Verificacion.invalido();
        }
        if (!iguales(firma("QR", sesionId, ventana), partes[3])) {
            return Verificacion.invalido();
        }
        // Firma buena pero ventana fuera de rango: el QR es auténtico y venció. Se
        // distingue del inválido para poder decirle a la persona "vuelve a escanear".
        return estaVigente(ventana) ? Verificacion.valido(sesionId) : Verificacion.vencido(sesionId);
    }

    /** ¿El código escrito a mano corresponde a esa sesión en una ventana todavía válida? */
    public boolean codigoCoincide(String sesionId, String codigo) {
        if (codigo == null) {
            return false;
        }
        String limpio = codigo.replaceAll("\\D", "");
        if (limpio.length() != DIGITOS_CODIGO) {
            return false;
        }
        long actual = ventanaActual();
        for (long ventana = actual - reglas.ventanasDeGracia(); ventana <= actual; ventana++) {
            if (iguales(codigoDe(sesionId, ventana), limpio)) {
                return true;
            }
        }
        return false;
    }

    String codigoDe(String sesionId, long ventana) {
        byte[] hmac = hmac("CODIGO|" + sesionId + "|" + ventana);
        // Truncamiento dinámico del RFC 4226 (el de los códigos de un solo uso): toma 31
        // bits desde un desplazamiento que depende del propio HMAC.
        int desplazamiento = hmac[hmac.length - 1] & 0x0F;
        int binario = ((hmac[desplazamiento] & 0x7F) << 24)
                | ((hmac[desplazamiento + 1] & 0xFF) << 16)
                | ((hmac[desplazamiento + 2] & 0xFF) << 8)
                | (hmac[desplazamiento + 3] & 0xFF);
        return String.format("%06d", binario % 1_000_000);
    }

    private boolean estaVigente(long ventana) {
        long actual = ventanaActual();
        return ventana <= actual && ventana >= actual - reglas.ventanasDeGracia();
    }

    private String firma(String proposito, String sesionId, long ventana) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(proposito + "|" + VERSION + "|" + sesionId + "|" + ventana));
    }

    private byte[] hmac(String mensaje) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token de asistencia.", e);
        }
    }

    private static boolean iguales(String esperado, String recibido) {
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sin clave configurada se genera una aleatoria en cada arranque. La aplicación
     * levanta igual —en local nadie quiere pelearse con una variable para probar—, pero los
     * QR emitidos antes del reinicio dejan de valer, y eso tiene que verse en el log.
     */
    private static byte[] bytesDeLaClave(String claveConfigurada) {
        if (claveConfigurada != null && !claveConfigurada.isBlank()) {
            return claveConfigurada.getBytes(StandardCharsets.UTF_8);
        }
        byte[] aleatoria = new byte[32];
        new SecureRandom().nextBytes(aleatoria);
        log.warn("ASISTENCIA_TOKEN_KEY no está definida: se generó una clave temporal. "
                + "Los QR y los códigos dejan de valer en cada reinicio. Define la variable "
                + "para un comportamiento estable.");
        return aleatoria;
    }

    /** Resultado de comprobar un token. */
    public record Verificacion(Estado estado, String sesionId) {

        public enum Estado { VALIDO, VENCIDO, INVALIDO }

        public static Verificacion valido(String sesionId) {
            return new Verificacion(Estado.VALIDO, sesionId);
        }

        public static Verificacion vencido(String sesionId) {
            return new Verificacion(Estado.VENCIDO, sesionId);
        }

        public static Verificacion invalido() {
            return new Verificacion(Estado.INVALIDO, null);
        }

        public boolean esValido() {
            return estado == Estado.VALIDO;
        }
    }
}
