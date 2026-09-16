package com.schoolplus.services.auditoria;

import com.schoolplus.model.AuditoriaAcceso;
import com.schoolplus.model.EventoAcceso;
import com.schoolplus.repository.AuditoriaAccesoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Bitácora de accesos (§4.1). Pieza nueva: ninguno de los tres repos de referencia la tenía.
 *
 * <p>Cada registro se escribe en su propia transacción ({@code REQUIRES_NEW}) para que un
 * intento de login fallido quede anotado aunque la transacción que lo rodea se deshaga.
 * Y ningún fallo de la bitácora puede tumbar una autenticación: se traga la excepción y se
 * queja en el log.</p>
 */
@Service
public class AuditoriaAccesoService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAccesoService.class);

    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_DETALLE = 255;

    private final AuditoriaAccesoRepository repositorio;

    public AuditoriaAccesoService(AuditoriaAccesoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(EventoAcceso evento, String email, String usuarioId,
                          HttpServletRequest peticion, String detalle) {
        try {
            AuditoriaAcceso registro = new AuditoriaAcceso();
            registro.setEvento(evento);
            registro.setEmailIntentado(normalizarEmail(email));
            registro.setUsuarioId(usuarioId);
            registro.setIp(ipDe(peticion));
            registro.setUserAgent(recortar(cabecera(peticion), MAX_USER_AGENT));
            registro.setCorrelationId(MDC.get("correlationId"));
            registro.setDetalle(recortar(detalle, MAX_DETALLE));
            registro.setOcurridoEn(LocalDateTime.now());
            repositorio.save(registro);
        } catch (RuntimeException e) {
            log.error("No se pudo escribir la bitácora de acceso ({} / {}): {}",
                    evento, email, e.getMessage(), e);
        }
    }

    public void registrar(EventoAcceso evento, String email, HttpServletRequest peticion) {
        registrar(evento, email, null, peticion, null);
    }

    /**
     * IP del cliente.
     *
     * <p>En producción la aplicación vive detrás de nginx, así que {@code getRemoteAddr()}
     * devolvería siempre la del proxy y el límite de intentos por IP no serviría de nada.
     * Se lee {@code X-Forwarded-For}, cuyo primer elemento es el cliente original.</p>
     */
    public String ipDe(HttpServletRequest peticion) {
        if (peticion == null) {
            return null;
        }
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].trim();
            if (!primera.isEmpty()) {
                return recortar(primera, 45);
            }
        }
        return recortar(peticion.getRemoteAddr(), 45);
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String cabecera(HttpServletRequest peticion) {
        return peticion == null ? null : peticion.getHeader("User-Agent");
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
