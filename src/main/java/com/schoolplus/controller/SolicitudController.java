package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.SolicitudPendiente;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.model.Solicitud;
import com.schoolplus.model.SolicitudTipo;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.VacacionesSaldo;
import com.schoolplus.repository.SolicitudRepository;
import com.schoolplus.repository.SolicitudTipoRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.solicitudes.NoAutorizadoADecidirException;
import com.schoolplus.services.solicitudes.NotificadorSolicitudes;
import com.schoolplus.services.solicitudes.SaldoInsuficienteException;
import com.schoolplus.services.solicitudes.SolicitudWorkflowService;
import com.schoolplus.services.solicitudes.TransicionInvalidaException;
import com.schoolplus.services.vacaciones.VacacionesService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Solicitudes de permisos y vacaciones: alta, bandeja del coordinador y bandeja de
 * Talento Humano (§4.4, §4.5 y las vistas mínimas del §7).
 *
 * <p>El controlador no decide nada de negocio: valida la entrada, llama al motor y avisa.
 * Toda la máquina de estados vive en {@link SolicitudWorkflowService}.</p>
 */
@Controller
public class SolicitudController {

    /** Etiquetas de estado para la vista. No van en el enum: el enum es del dominio. */
    private static final Map<EstadoSolicitud, String> ETIQUETAS = new EnumMap<>(EstadoSolicitud.class);

    static {
        ETIQUETAS.put(EstadoSolicitud.BORRADOR, "Borrador");
        ETIQUETAS.put(EstadoSolicitud.PENDIENTE_COORDINADOR, "Con el coordinador");
        ETIQUETAS.put(EstadoSolicitud.PENDIENTE_SUPERVISOR, "Con Talento Humano");
        ETIQUETAS.put(EstadoSolicitud.APROBADO, "Aprobada");
        ETIQUETAS.put(EstadoSolicitud.RECHAZADO_COORD, "Denegada por coordinación");
        ETIQUETAS.put(EstadoSolicitud.RECHAZADO_RRHH, "Denegada por Talento Humano");
    }

    /** Estados que ocupan calendario y por tanto cuentan como cruce (§4.5). */
    private static final List<EstadoSolicitud> OCUPAN_CALENDARIO = List.of(
            EstadoSolicitud.PENDIENTE_COORDINADOR,
            EstadoSolicitud.PENDIENTE_SUPERVISOR,
            EstadoSolicitud.APROBADO);

    private static final int POR_PAGINA = 50;

    private final SolicitudWorkflowService workflow;
    private final SolicitudRepository solicitudRepository;
    private final SolicitudTipoRepository tipoRepository;
    private final VacacionesService vacacionesService;
    private final NotificadorSolicitudes notificador;
    private final com.schoolplus.services.solicitudes.DiasHabilesService diasHabilesService;

    public SolicitudController(SolicitudWorkflowService workflow,
                               SolicitudRepository solicitudRepository,
                               SolicitudTipoRepository tipoRepository,
                               VacacionesService vacacionesService,
                               NotificadorSolicitudes notificador,
                               com.schoolplus.services.solicitudes.DiasHabilesService diasHabilesService) {
        this.workflow = workflow;
        this.solicitudRepository = solicitudRepository;
        this.tipoRepository = tipoRepository;
        this.vacacionesService = vacacionesService;
        this.notificador = notificador;
        this.diasHabilesService = diasHabilesService;
    }

    // ── Mis solicitudes ──────────────────────────────────────────────────────

    /*
     * Sin @Transactional en el método, a propósito. Aquí se mezclan una escritura (poner
     * el saldo al día) y varias lecturas, y envolverlo todo en una transacción de solo
     * lectura hacía fallar la escritura con "Connection is read-only". Cada servicio
     * abre la suya, que es lo correcto: la causación necesita escribir y los listados no.
     */
    @GetMapping("/mi-espacio/solicitudes")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_CREAR + "')")
    public String misSolicitudes(@Actor Usuario yo, Model modelo) {
        // Poner el saldo al día al abrir la pantalla: es idempotente, así que no hace
        // falta un job para que la cifra sea correcta cuando alguien la mira.
        vacacionesService.causarHasta(yo, LocalDate.now());

        List<VacacionesSaldo> saldos = vacacionesService.saldosDe(yo.getId());
        modelo.addAttribute("saldoCausados", sumar(saldos, VacacionesSaldo::getDiasCausados));
        modelo.addAttribute("saldoTomados", sumar(saldos, VacacionesSaldo::getDiasTomados));
        modelo.addAttribute("saldoEnTramite", sumar(saldos, VacacionesSaldo::getDiasEnTramite));
        modelo.addAttribute("saldoDisponible", sumar(saldos, VacacionesSaldo::disponible));
        modelo.addAttribute("diasPorMes", vacacionesService.getDiasPorMes());

        modelo.addAttribute("solicitudes",
                solicitudRepository.misSolicitudes(yo.getId(), PageRequest.of(0, POR_PAGINA)).getContent());
        modelo.addAttribute("etiquetasEstado", ETIQUETAS);
        modelo.addAttribute("seccion", "solicitudes");
        return "solicitudes/mis-solicitudes";
    }

    @GetMapping("/mi-espacio/solicitudes/nueva")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_CREAR + "')")
    @Transactional(readOnly = true)
    public String formularioNueva(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("tipos", tipoRepository.findByActivoTrueOrderByNombreAsc());
        modelo.addAttribute("coordinador",
                yo.getCoordinador() == null ? null : yo.getCoordinador().getNombre());
        modelo.addAttribute("diasHabilesTexto", textoDiasHabiles());
        modelo.addAttribute("seccion", "solicitudes");
        return "solicitudes/nueva";
    }

    @PostMapping("/mi-espacio/solicitudes")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_CREAR + "')")
    public String crear(@Actor Usuario yo,
                        @RequestParam("tipoCodigo") String tipoCodigo,
                        @RequestParam("fechaInicio") String fechaInicioTexto,
                        @RequestParam("fechaFin") String fechaFinTexto,
                        @RequestParam(value = "motivo", required = false) String motivo,
                        @RequestParam(value = "soporte", required = false) MultipartFile soporte,
                        RedirectAttributes redirect) {

        SolicitudTipo tipo = tipoRepository.findById(tipoCodigo)
                .orElseThrow(() -> new TransicionInvalidaException("Ese tipo de solicitud no existe."));

        LocalDate inicio;
        LocalDate fin;
        try {
            inicio = LocalDate.parse(fechaInicioTexto);
            fin = LocalDate.parse(fechaFinTexto);
        } catch (DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Esa fecha no es válida. Revisá el día, mes y año.");
            return "redirect:/mi-espacio/solicitudes/nueva";
        }

        if (tipo.isRequiereSoporte() && (motivo == null || motivo.isBlank())) {
            redirect.addFlashAttribute("error",
                    tipo.getNombre() + " exige explicar el motivo.");
            return "redirect:/mi-espacio/solicitudes/nueva";
        }

        try {
            Solicitud solicitud = workflow.crearBorrador(yo, tipo, inicio, fin, motivo, soporte);
            solicitud = workflow.enviar(solicitud.getId(), yo);

            // Fuera de la transacción del cambio de estado (§5.2).
            avisarSegunDestino(solicitud);

            redirect.addFlashAttribute("aviso", solicitud.getEstado() == EstadoSolicitud.PENDIENTE_SUPERVISOR
                    ? "Solicitud enviada a Talento Humano."
                    : "Solicitud enviada a tu coordinador.");
        } catch (SaldoInsuficienteException | TransicionInvalidaException | NoAutorizadoADecidirException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-espacio/solicitudes/nueva";
        }
        return "redirect:/mi-espacio/solicitudes";
    }

    @GetMapping("/mi-espacio/solicitudes/{id}/soporte")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_CREAR + "')"
            + " or @capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_PRIMERA + "')"
            + " or @capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_SEGUNDA + "')")
    public ResponseEntity<InputStreamResource> soporte(@Actor Usuario yo, @PathVariable String id) {
        ArchivoDescargado archivo;
        try {
            archivo = workflow.descargarSoporte(yo, id);
        } catch (NoAutorizadoADecidirException e) {
            return ResponseEntity.status(403).build();
        } catch (TransicionInvalidaException e) {
            return ResponseEntity.notFound().build();
        }
        String mime = archivo.mime() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : archivo.mime();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.nombreArchivo() + "\"")
                .contentLength(archivo.tamanoBytes())
                .body(new InputStreamResource(archivo.contenido()));
    }

    // ── Bandeja del coordinador ──────────────────────────────────────────────

    @GetMapping("/coordinacion/bandeja")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_PRIMERA + "')")
    @Transactional(readOnly = true)
    public String bandejaCoordinador(@Actor Usuario yo, Model modelo) {
        List<Solicitud> pendientes = solicitudRepository
                .bandejaCoordinador(yo.getId(), PageRequest.of(0, POR_PAGINA)).getContent();

        modelo.addAttribute("solicitudes", conCruces(pendientes, yo.getId()));
        modelo.addAttribute("rutaBase", "/coordinacion/bandeja");
        modelo.addAttribute("seccion", "bandeja-coord");
        modelo.addAttribute("antetitulo", "Coordinación");
        modelo.addAttribute("titulo", "Primera instancia");
        modelo.addAttribute("descripcion",
                "Solicitudes de la gente a tu cargo. Si apruebas, pasan a Talento Humano; "
                        + "si deniegas, el trámite termina aquí y no llega a RRHH.");
        return "solicitudes/bandeja";
    }

    @PostMapping("/coordinacion/bandeja/{id}/aprobar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_PRIMERA + "')")
    public String aprobarCoordinador(@PathVariable String id, @Actor Usuario actor,
                                     RedirectAttributes redirect) {
        return decidir(redirect, "/coordinacion/bandeja", () -> {
            Solicitud s = workflow.aprobarCoordinador(id, actor, "Aprobada por coordinación");
            notificador.avisarDecision(s, EstadoSolicitud.PENDIENTE_SUPERVISOR, null);
            return "Aprobada. Pasa a Talento Humano.";
        });
    }

    @PostMapping("/coordinacion/bandeja/{id}/rechazar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_PRIMERA + "')")
    public String rechazarCoordinador(@PathVariable String id,
                                      @RequestParam("motivo") String motivo,
                                      @Actor Usuario actor,
                                      RedirectAttributes redirect) {
        return decidir(redirect, "/coordinacion/bandeja", () -> {
            Solicitud s = workflow.rechazarCoordinador(id, actor, motivo);
            notificador.avisarDecision(s, EstadoSolicitud.RECHAZADO_COORD, motivo);
            return "Denegada. El trámite termina aquí.";
        });
    }

    // ── Bandeja de Talento Humano ────────────────────────────────────────────

    @GetMapping("/talento-humano/bandeja")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_SEGUNDA + "')")
    @Transactional(readOnly = true)
    public String bandejaSupervisor(Model modelo) {
        List<Solicitud> pendientes = solicitudRepository
                .bandejaSupervisor(PageRequest.of(0, POR_PAGINA)).getContent();

        modelo.addAttribute("solicitudes", conCruces(pendientes, null));
        modelo.addAttribute("rutaBase", "/talento-humano/bandeja");
        modelo.addAttribute("seccion", "bandeja-rrhh");
        modelo.addAttribute("antetitulo", "Talento Humano");
        modelo.addAttribute("titulo", "Segunda instancia");
        modelo.addAttribute("descripcion",
                "Última palabra. Lo que apruebes aquí queda validado; lo que deniegues no "
                        + "queda validado y el trámite termina.");
        return "solicitudes/bandeja";
    }

    @PostMapping("/talento-humano/bandeja/{id}/aprobar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_SEGUNDA + "')")
    public String aprobarSupervisor(@PathVariable String id, @Actor Usuario actor,
                                    RedirectAttributes redirect) {
        return decidir(redirect, "/talento-humano/bandeja", () -> {
            Solicitud s = workflow.aprobarSupervisor(id, actor, "Validada por Talento Humano");
            notificador.avisarDecision(s, EstadoSolicitud.APROBADO, null);
            return "Aprobada y validada.";
        });
    }

    @PostMapping("/talento-humano/bandeja/{id}/rechazar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.SOLICITUD_APROBAR_SEGUNDA + "')")
    public String rechazarSupervisor(@PathVariable String id,
                                     @RequestParam("motivo") String motivo,
                                     @Actor Usuario actor,
                                     RedirectAttributes redirect) {
        return decidir(redirect, "/talento-humano/bandeja", () -> {
            Solicitud s = workflow.rechazarSupervisor(id, actor, motivo);
            notificador.avisarDecision(s, EstadoSolicitud.RECHAZADO_RRHH, motivo);
            return "Denegada. El permiso no queda validado.";
        });
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    /** Envuelve las cuatro decisiones: mismo manejo de errores para las cuatro. */
    private String decidir(RedirectAttributes redirect, String destino, Decision decision) {
        try {
            redirect.addFlashAttribute("aviso", decision.ejecutar());
        } catch (TransicionInvalidaException e) {
            // Caso típico: el doble clic, o alguien que decidió mientras tú mirabas.
            redirect.addFlashAttribute("error",
                    "Esa solicitud ya no está en tu bandeja: " + e.getMessage());
        } catch (NoAutorizadoADecidirException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino;
    }

    @FunctionalInterface
    private interface Decision {
        String ejecutar();
    }

    private void avisarSegunDestino(Solicitud solicitud) {
        if (solicitud.getEstado() == EstadoSolicitud.PENDIENTE_COORDINADOR) {
            notificador.avisarPendiente(solicitud, solicitud.getCoordinadorAsignado());
        }
        // Con Talento Humano no hay un destinatario único: la bandeja es compartida.
        // Avisar a todos los SUPERVISOR llega con las notificaciones del hito 6.
    }

    /*
     * Sin clave de idempotencia en el formulario, y es deliberado. El §5.2 admite dos
     * caminos: "Idempotency-Key o validación de estado previo dentro de la transacción".
     * Está el segundo, que para este flujo es más fuerte: el doble clic falla porque la
     * solicitud ya no está en el estado que la transición exige, y eso vale también para
     * un reenvío del formulario media hora después. La columna `idempotency_key` queda en
     * el esquema para cuando haya API REST, donde el cliente sí manda la clave.
     */
    private List<SolicitudPendiente> conCruces(List<Solicitud> pendientes, String coordinadorId) {
        return pendientes.stream()
                .map(s -> new SolicitudPendiente(s, crucesDe(s, coordinadorId)))
                .toList();
    }

    private List<Solicitud> crucesDe(Solicitud solicitud, String coordinadorId) {
        Usuario solicitante = solicitud.getSolicitante();
        String jefe = coordinadorId != null
                ? coordinadorId
                : (solicitante.getCoordinador() == null ? null : solicitante.getCoordinador().getId());
        if (jefe == null) {
            return List.of();
        }
        return solicitudRepository.crucesDelEquipo(
                jefe, solicitante.getId(),
                solicitud.getFechaInicio(), solicitud.getFechaFin(),
                OCUPAN_CALENDARIO);
    }

    private String textoDiasHabiles() {
        return diasHabilesService.getDiasHabiles().size() == 5 ? "lunes a viernes" : "los configurados";
    }

    private BigDecimal sumar(List<VacacionesSaldo> saldos,
                             java.util.function.Function<VacacionesSaldo, BigDecimal> campo) {
        return saldos.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
