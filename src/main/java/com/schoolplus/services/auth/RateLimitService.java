package com.schoolplus.services.auth;

import com.schoolplus.model.EventoAcceso;
import com.schoolplus.repository.AuditoriaAccesoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Límite de intentos en login y en recuperación de contraseña (§4.1).
 *
 * <p>Pieza nueva: ninguno de los tres repos de referencia limitaba nada — hallazgo #3 de
 * docs/DECISIONES.md</p>
 *
 * <p><strong>Por qué cuenta filas en vez de usar un contador en memoria.</strong> El §5.3
 * exige un backend sin estado en el nodo. Un {@code Map} de intentos moriría en cada
 * despliegue y, con dos instancias detrás del balanceador, daría el doble de margen al que
 * ataca. La bitácora de accesos ya guarda cada intento con su marca de tiempo, así que el
 * límite se resuelve con un {@code COUNT} sobre índice y funciona igual con una instancia
 * que con cinco. Ver docs/DECISIONES.md D-010.</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final AuditoriaAccesoRepository auditoria;

    private final int loginMaxPorCuenta;
    private final int loginMaxPorIp;
    private final int loginVentanaMinutos;
    private final int resetMaxPorCuenta;
    private final int resetMaxPorIp;
    private final int resetVentanaMinutos;

    public RateLimitService(
            AuditoriaAccesoRepository auditoria,
            @Value("${app.security.rate-limit.login.max-por-cuenta:5}") int loginMaxPorCuenta,
            @Value("${app.security.rate-limit.login.max-por-ip:20}") int loginMaxPorIp,
            @Value("${app.security.rate-limit.login.ventana-minutos:15}") int loginVentanaMinutos,
            @Value("${app.security.rate-limit.reset.max-por-cuenta:3}") int resetMaxPorCuenta,
            @Value("${app.security.rate-limit.reset.max-por-ip:10}") int resetMaxPorIp,
            @Value("${app.security.rate-limit.reset.ventana-minutos:60}") int resetVentanaMinutos) {
        this.auditoria = auditoria;
        this.loginMaxPorCuenta = loginMaxPorCuenta;
        this.loginMaxPorIp = loginMaxPorIp;
        this.loginVentanaMinutos = loginVentanaMinutos;
        this.resetMaxPorCuenta = resetMaxPorCuenta;
        this.resetMaxPorIp = resetMaxPorIp;
        this.resetVentanaMinutos = resetVentanaMinutos;
    }

    /** ¿Se agotaron los intentos de login de esta cuenta o de esta IP? */
    @Transactional(readOnly = true)
    public boolean loginBloqueado(String email, String ip) {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(loginVentanaMinutos);
        return excede(EventoAcceso.LOGIN_FALLIDO, email, ip, desde, loginMaxPorCuenta, loginMaxPorIp);
    }

    /** ¿Se pidieron ya demasiados enlaces de recuperación? */
    @Transactional(readOnly = true)
    public boolean recuperacionBloqueada(String email, String ip) {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(resetVentanaMinutos);
        return excede(EventoAcceso.RESET_SOLICITADO, email, ip, desde, resetMaxPorCuenta, resetMaxPorIp);
    }

    private boolean excede(EventoAcceso evento, String email, String ip,
                           LocalDateTime desde, int maxPorCuenta, int maxPorIp) {
        try {
            String correo = email == null ? null : email.trim().toLowerCase();
            if (correo != null && !correo.isEmpty()) {
                long porCuenta = auditoria
                        .countByEmailIntentadoAndEventoAndOcurridoEnAfter(correo, evento, desde);
                if (porCuenta >= maxPorCuenta) {
                    log.warn("Límite de {} alcanzado para la cuenta {} ({} en la ventana)",
                            evento, correo, porCuenta);
                    return true;
                }
            }
            if (ip != null && !ip.isBlank()) {
                long porIp = auditoria.countByIpAndEventoAndOcurridoEnAfter(ip, evento, desde);
                if (porIp >= maxPorIp) {
                    log.warn("Límite de {} alcanzado para la IP {} ({} en la ventana)",
                            evento, ip, porIp);
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            // Si la consulta falla, se deja pasar: es preferible a dejar a toda la
            // institución sin poder entrar por un problema de la bitácora.
            log.error("No se pudo evaluar el límite de intentos ({}): {}", evento, e.getMessage(), e);
            return false;
        }
    }
}
