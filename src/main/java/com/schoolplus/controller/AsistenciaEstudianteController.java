package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.configuration.web.CookieDispositivo;
import com.schoolplus.dto.ResumenAsistencia;
import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoAsistencia;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.asistencia.AsistenciaService;
import com.schoolplus.services.asistencia.EscaneoPendiente;
import com.schoolplus.services.asistencia.MotivoRechazo;
import com.schoolplus.services.asistencia.ReglasAsistencia;
import com.schoolplus.services.asistencia.TokenAsistenciaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las pantallas del estudiante, pensadas para un celular en la mano y con prisa.
 *
 * <p>Tres caminos para lo mismo:</p>
 * <ol>
 *   <li><strong>Lector de la app:</strong> {@code /estudiante/asistencia} abre la cámara
 *       dentro de la página.</li>
 *   <li><strong>Cámara nativa del teléfono:</strong> el QR es un enlace a
 *       {@code /asistencia/r}, que es público. Ahí se comprueba el token —todavía vigente—,
 *       se guarda el escaneo en la sesión y, si hace falta, se pide iniciar sesión.</li>
 *   <li><strong>Código de 6 dígitos:</strong> para quien niega el permiso de cámara o no
 *       tiene, y lo escribe a mano.</li>
 * </ol>
 */
@Controller
public class AsistenciaEstudianteController {

    private final AsistenciaService asistenciaService;
    private final TokenAsistenciaService tokens;
    private final ReglasAsistencia reglas;
    private final CookieDispositivo cookieDispositivo;
    private final Clock reloj;

    public AsistenciaEstudianteController(AsistenciaService asistenciaService,
                                          TokenAsistenciaService tokens,
                                          ReglasAsistencia reglas,
                                          CookieDispositivo cookieDispositivo,
                                          Clock reloj) {
        this.asistenciaService = asistenciaService;
        this.tokens = tokens;
        this.reglas = reglas;
        this.cookieDispositivo = cookieDispositivo;
        this.reloj = reloj;
    }

    @GetMapping("/estudiante/asistencia")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REGISTRAR + "')")
    public String registrar(@Actor Usuario actor, HttpServletRequest peticion, HttpServletResponse respuesta,
                            Model modelo) {
        // La cookie se asegura al pintar la pantalla, no al registrar: así ya viaja en el
        // POST y no dependemos de que el navegador la acepte a mitad de la petición de fondo.
        cookieDispositivo.asegurar(peticion, respuesta);
        modelo.addAttribute("clases", asistenciaService.clasesDelEstudiante(actor));
        modelo.addAttribute("geoRequerida", reglas.geoHabilitada());
        modelo.addAttribute("seccion", "asistencia-estudiante");
        return "asistencia/estudiante/registrar";
    }

    /**
     * Destino del QR. Es público a propósito: el estudiante que escanea con la cámara del
     * teléfono puede no tener sesión abierta en ese navegador, y el token tiene que
     * comprobarse <em>ahora</em>, mientras la ventana sigue viva.
     */
    @GetMapping("/asistencia/r")
    public String desdeElQr(@RequestParam(value = "t", required = false) String token,
                            HttpServletRequest peticion, HttpServletResponse respuesta, Model modelo) {
        cookieDispositivo.asegurar(peticion, respuesta);
        TokenAsistenciaService.Verificacion verificacion = tokens.verificar(token);
        if (!verificacion.esValido()) {
            MotivoRechazo motivo = verificacion.estado() == TokenAsistenciaService.Verificacion.Estado.VENCIDO
                    ? MotivoRechazo.QR_VENCIDO
                    : MotivoRechazo.QR_INVALIDO;
            modelo.addAttribute("titulo", motivo.titulo());
            modelo.addAttribute("mensaje", motivo.mensaje());
            return "asistencia/escaneo-invalido";
        }
        peticion.getSession(true).setAttribute(EscaneoPendiente.ATRIBUTO,
                new EscaneoPendiente(verificacion.sesionId(), LocalDateTime.now(reloj)));
        // Si no hay sesión iniciada, esta ruta exige login y, al volver, el manejador de
        // entrada correcta ve el escaneo pendiente y trae de vuelta aquí.
        return "redirect:/estudiante/asistencia/confirmar";
    }

    @GetMapping("/estudiante/asistencia/confirmar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REGISTRAR + "')")
    public String confirmar(HttpServletRequest peticion, HttpServletResponse respuesta, Model modelo) {
        Object pendiente = peticion.getSession(true).getAttribute(EscaneoPendiente.ATRIBUTO);
        if (pendiente == null) {
            return "redirect:/estudiante/asistencia";
        }
        cookieDispositivo.asegurar(peticion, respuesta);
        modelo.addAttribute("geoRequerida", reglas.geoHabilitada());
        modelo.addAttribute("seccion", "asistencia-estudiante");
        return "asistencia/estudiante/confirmar";
    }

    @GetMapping("/estudiante/asistencia/historial")
    @PreAuthorize("@capacidades.puede('" + Capacidades.ASISTENCIA_REGISTRAR + "')")
    public String historial(@Actor Usuario actor, Model modelo) {
        List<AsistenciaRegistro> registros = asistenciaService.historialDe(actor);
        modelo.addAttribute("registros", registros);
        modelo.addAttribute("porClase", resumirPorClase(registros));
        modelo.addAttribute("seccion", "asistencia-historial");
        return "asistencia/estudiante/historial";
    }

    /**
     * El porcentaje propio de cada clase.
     *
     * <p>Se calcula aquí y no con {@code AsistenciaReporteService} porque ese exige la
     * capacidad {@code ASISTENCIA_REPORTE}, que un estudiante no tiene ni debe tener: lo
     * suyo no es un reporte de la institución, es su propia ficha.</p>
     */
    private List<ResumenAsistencia> resumirPorClase(List<AsistenciaRegistro> registros) {
        Map<String, List<AsistenciaRegistro>> porClase = new LinkedHashMap<>();
        for (AsistenciaRegistro registro : registros) {
            // Solo cuentan las sesiones ya cerradas: en una abierta todavía no hay ausentes.
            if (registro.getSesion().estaAbierta()) {
                continue;
            }
            porClase.computeIfAbsent(registro.getSesion().getClase().getId(), k -> new ArrayList<>())
                    .add(registro);
        }
        List<ResumenAsistencia> resumenes = new ArrayList<>();
        porClase.forEach((claseId, delGrupo) -> resumenes.add(new ResumenAsistencia(
                claseId,
                delGrupo.get(0).getSesion().getClase().etiqueta(),
                delGrupo.get(0).getSesion().getClase().getProfesor().getNombre(),
                contar(delGrupo, EstadoAsistencia.PRESENTE),
                contar(delGrupo, EstadoAsistencia.TARDE),
                contar(delGrupo, EstadoAsistencia.AUSENTE),
                contar(delGrupo, EstadoAsistencia.JUSTIFICADO))));
        return resumenes;
    }

    private long contar(List<AsistenciaRegistro> registros, EstadoAsistencia estado) {
        return registros.stream().filter(r -> r.getEstado() == estado).count();
    }
}
