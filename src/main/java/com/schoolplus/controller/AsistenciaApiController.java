package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.configuration.web.CookieDispositivo;
import com.schoolplus.dto.SesionEnVivo;
import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.asistencia.AsistenciaEnVivoHandler;
import com.schoolplus.services.asistencia.AsistenciaService;
import com.schoolplus.services.asistencia.ContextoRegistro;
import com.schoolplus.services.asistencia.EscaneoPendiente;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.format.DateTimeFormatter;

/**
 * La API del módulo de asistencia: la pantalla de proyección pide el estado cada vez que el
 * QR rota, y el celular del estudiante manda su registro.
 *
 * <p>Los rechazos previsibles (QR vencido, no inscrito, celular no autorizado…) los traduce
 * {@link ManejadorDeAsistencia} a un 422 con el texto que hay que mostrar, no a un 500.</p>
 */
@RestController
@RequestMapping("/api/v1/asistencia")
public class AsistenciaApiController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AsistenciaService asistenciaService;
    private final AsistenciaEnVivoHandler enVivo;
    private final CookieDispositivo cookieDispositivo;
    private final AuditoriaAccesoService auditoria;

    public AsistenciaApiController(AsistenciaService asistenciaService,
                                   AsistenciaEnVivoHandler enVivo,
                                   CookieDispositivo cookieDispositivo,
                                   AuditoriaAccesoService auditoria) {
        this.asistenciaService = asistenciaService;
        this.enVivo = enVivo;
        this.cookieDispositivo = cookieDispositivo;
        this.auditoria = auditoria;
    }

    /** Lo que pide el estudiante: token del QR, o código escrito, o el escaneo pendiente. */
    public record RegistroPeticion(String token, String codigo, Boolean pendiente,
                                   BigDecimal latitud, BigDecimal longitud) {
    }

    /** Lo que pinta la pantalla verde de confirmación. */
    public record RegistroRespuesta(boolean ok, String titulo, String mensaje, String clase,
                                    String hora, String estado) {
    }

    @GetMapping("/sesiones/{id}/estado")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_TOMAR + "')")
    public SesionEnVivo estado(@PathVariable String id, @Actor Usuario actor) {
        return asistenciaService.enVivo(actor, id);
    }

    @PostMapping("/registros")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REGISTRAR + "')")
    public RegistroRespuesta registrar(@RequestBody RegistroPeticion peticion,
                                       @Actor Usuario actor,
                                       HttpServletRequest solicitud,
                                       HttpSession sesionHttp) {
        ContextoRegistro contexto = new ContextoRegistro(
                cookieDispositivo.leer(solicitud),
                auditoria.ipDe(solicitud),
                solicitud.getHeader(HttpHeaders.USER_AGENT),
                peticion.latitud(),
                peticion.longitud());

        AsistenciaRegistro registro = registrarSegunElCamino(peticion, actor, contexto, sesionHttp);

        // Ya está confirmada la transacción del servicio: recién ahora se avisa a la
        // pantalla del profesor para que el contador suba.
        enVivo.avisar(registro.getSesion().getId());

        return new RegistroRespuesta(
                true,
                "¡Listo, " + primerNombre(actor) + "!",
                "Quedaste " + registro.getEstado().etiqueta().toLowerCase() + " en esta clase.",
                registro.getSesion().getClase().etiqueta(),
                registro.getRegistradoEn().format(HORA),
                registro.getEstado().name());
    }

    private AsistenciaRegistro registrarSegunElCamino(RegistroPeticion peticion, Usuario actor,
                                                      ContextoRegistro contexto, HttpSession sesionHttp) {
        if (Boolean.TRUE.equals(peticion.pendiente())) {
            EscaneoPendiente pendiente = (EscaneoPendiente) sesionHttp.getAttribute(EscaneoPendiente.ATRIBUTO);
            // Se consume: un escaneo sirve una vez, aunque el registro falle.
            sesionHttp.removeAttribute(EscaneoPendiente.ATRIBUTO);
            return asistenciaService.registrarEscaneoPendiente(actor,
                    pendiente == null ? null : pendiente.sesionId(),
                    pendiente == null ? null : pendiente.escaneadoEn(),
                    contexto);
        }
        if (peticion.codigo() != null && !peticion.codigo().isBlank()) {
            return asistenciaService.registrarConCodigo(actor, peticion.codigo(), contexto);
        }
        return asistenciaService.registrarConToken(actor, tokenDe(peticion.token()), contexto);
    }

    /**
     * El lector devuelve el contenido del QR, que es la URL entera. Se acepta tal cual o ya
     * recortada al token: al estudiante le da igual y así el mismo endpoint sirve para el
     * lector de la app y para cualquier otro.
     */
    static String tokenDe(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return null;
        }
        String texto = contenido.trim();
        if (!texto.contains("/asistencia/r")) {
            return texto;
        }
        try {
            String consulta = URI.create(texto).getQuery();
            if (consulta == null) {
                return texto;
            }
            for (String parametro : consulta.split("&")) {
                if (parametro.startsWith("t=")) {
                    return parametro.substring(2);
                }
            }
            return texto;
        } catch (IllegalArgumentException e) {
            return texto;
        }
    }

    private static String primerNombre(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        int espacio = nombre.indexOf(' ');
        return espacio > 0 ? nombre.substring(0, espacio) : nombre;
    }
}
