package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.dto.DestacadoPortal;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.TipoPublicacion;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.capacitacion.CursoService;
import com.schoolplus.services.documentosinstitucionales.DocumentoInstitucionalService;
import com.schoolplus.services.muro.MuroService;
import com.schoolplus.services.permisos.CapacidadService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Portal / muro (§4.7). Es la pestaña inicial de todos los roles.
 *
 * <p>Lo que cada quien puede hacer sale de la matriz de permisos, no del rol escrito a
 * mano: publicar y moderar exigen {@code PORTAL_PUBLICAR} y {@code PORTAL_MODERAR};
 * comentar, reaccionar y votar exigen {@code PORTAL_COMENTAR}. La plantilla pregunta lo
 * mismo para no pintar botones que darían 403.</p>
 *
 * <h2>Sin recarga de página (2026-09-04)</h2>
 * <p>Comentar, reaccionar, votar, fijar y moderar aceptan la cabecera {@code X-Ajax}: el
 * JS del muro la manda, la recarga normal de un navegador sin JS no. Con ella, el
 * método responde con el fragmento Thymeleaf de esa única publicación (mismo
 * {@code <article>} que pinta el feed) en vez de un 302 a {@code /portal} — el JS
 * reemplaza ese nodo del DOM y listo, sin recargar nada. Publicar sigue siendo una
 * recarga completa a propósito: inserta una entrada nueva al feed paginado, y eso sí
 * amerita traer la página de nuevo.</p>
 */
@Controller
public class MuroController {

    private final MuroService muroService;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadService capacidades;
    private final DocumentoInstitucionalService documentoInstitucionalService;
    private final CursoService cursoService;

    public MuroController(MuroService muroService,
                          UsuarioRepository usuarioRepository,
                          CapacidadService capacidades,
                          DocumentoInstitucionalService documentoInstitucionalService,
                          CursoService cursoService) {
        this.muroService = muroService;
        this.usuarioRepository = usuarioRepository;
        this.capacidades = capacidades;
        this.documentoInstitucionalService = documentoInstitucionalService;
        this.cursoService = cursoService;
    }

    @GetMapping("/portal")
    public String muro(@Actor Usuario yo,
                       @RequestParam(value = "tipo", required = false) TipoPublicacion tipo,
                       @RequestParam(value = "pagina", defaultValue = "0") int pagina,
                       Model modelo) {

        var feed = muroService.feed(yo, tipo, pagina);

        modelo.addAttribute("entradas", feed.getContent());
        modelo.addAttribute("paginaActual", feed.getNumber());
        modelo.addAttribute("totalPaginas", feed.getTotalPages());
        modelo.addAttribute("filtro", tipo);
        modelo.addAttribute("puedePublicar", capacidades.puede(Capacidades.PORTAL_PUBLICAR));
        modelo.addAttribute("puedeEncuestar", capacidades.puede(Capacidades.PORTAL_ENCUESTA_CREAR));
        modelo.addAttribute("puedeComentar", capacidades.puede(Capacidades.PORTAL_COMENTAR));
        modelo.addAttribute("yo", yo);
        modelo.addAttribute("seccion", "portal");

        modelo.addAttribute("destacadosPortal", destacadosPortal());
        return "muro/portal";
    }

    /**
     * Cursos publicados + documentos institucionales, mezclados en el carrusel del portal
     * (§ capacitaciones y § documentos institucionales piden lo mismo: mostrarse ahí).
     * Más reciente primero — cada tipo respeta su propia capacidad de ver.
     */
    private List<DestacadoPortal> destacadosPortal() {
        var destacados = new java.util.ArrayList<DestacadoPortal>();
        if (capacidades.puede(Capacidades.CAPACITACION_VER)) {
            cursoService.catalogo().forEach(curso -> destacados.add(DestacadoPortal.deCurso(curso)));
        }
        if (capacidades.puede(Capacidades.DOCUMENTO_INSTITUCIONAL_VER)) {
            documentoInstitucionalService.publicados()
                    .forEach(doc -> destacados.add(DestacadoPortal.deDocumento(doc)));
        }
        destacados.sort(java.util.Comparator.comparing(DestacadoPortal::creadoEn).reversed());
        return destacados;
    }

    @PostMapping("/portal/publicar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PORTAL_PUBLICAR + "')")
    public String publicar(@Actor Usuario actor,
                           @RequestParam("tipo") TipoPublicacion tipo,
                           @RequestParam(value = "titulo", required = false) String titulo,
                           @RequestParam("cuerpo") String cuerpo,
                           @RequestParam(value = "destinatarioId", required = false) String destinatarioId,
                           @RequestParam(value = "opciones", required = false) List<String> opciones,
                           RedirectAttributes redirect) {
        try {
            Usuario destinatario = destinatarioId == null || destinatarioId.isBlank()
                    ? null
                    : usuarioRepository.findById(destinatarioId).orElse(null);
            muroService.publicar(actor, tipo, titulo, cuerpo, destinatario, opciones);
            redirect.addFlashAttribute("aviso", "Publicado.");
        } catch (AccessDeniedException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal";
    }

    @PostMapping("/portal/{id}/comentar")
    public String comentar(@Actor Usuario actor, @PathVariable String id,
                           @RequestParam("cuerpo") String cuerpo,
                           @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                           HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, id, ajax, response, modelo, redirect, null,
                () -> muroService.comentar(actor, id, cuerpo));
    }

    @PostMapping("/portal/{id}/reaccionar")
    public String reaccionar(@Actor Usuario actor, @PathVariable String id,
                             @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                             HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, id, ajax, response, modelo, redirect, null,
                () -> muroService.alternarReaccion(actor, id));
    }

    @PostMapping("/portal/{id}/votar")
    public String votar(@Actor Usuario actor, @PathVariable String id,
                        @RequestParam("opcionId") String opcionId,
                        @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                        HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, id, ajax, response, modelo, redirect, "Voto registrado.",
                () -> muroService.votar(actor, id, opcionId));
    }

    @PostMapping("/portal/{id}/moderar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PORTAL_MODERAR + "')")
    public String moderar(@Actor Usuario actor, @PathVariable String id,
                          @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                          HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, id, ajax, response, modelo, redirect, "Visibilidad cambiada.",
                () -> muroService.alternarVisibilidad(actor, id));
    }

    @PostMapping("/portal/{id}/fijar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PORTAL_PUBLICAR + "')")
    public String fijar(@Actor Usuario actor, @PathVariable String id,
                        @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                        HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, id, ajax, response, modelo, redirect, "Listo.",
                () -> muroService.alternarFijada(actor, id));
    }

    /** La publicación es la que el JS necesita para refrescar, no el comentario: viaja aparte. */
    @PostMapping("/portal/comentarios/{id}/moderar")
    @PreAuthorize("@capacidades.puede('" + Capacidades.PORTAL_MODERAR + "')")
    public String moderarComentario(@Actor Usuario actor, @PathVariable String id,
                                    @RequestParam("publicacionId") String publicacionId,
                                    @RequestHeader(value = "X-Ajax", required = false) boolean ajax,
                                    HttpServletResponse response, Model modelo, RedirectAttributes redirect) {
        return interaccion(actor, publicacionId, ajax, response, modelo, redirect,
                "Comentario moderado.", () -> muroService.ocultarComentario(actor, id));
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    /**
     * Ejecuta la acción y responde según el canal: fragmento de esa publicación si vino
     * por AJAX, redirección con flash si vino de un formulario normal (progresivo: el
     * muro sigue funcionando sin JavaScript).
     */
    private String interaccion(Usuario actor, String publicacionId, boolean ajax,
                               HttpServletResponse response, Model modelo, RedirectAttributes redirect,
                               String avisoNoAjax, Runnable accion) {
        try {
            accion.run();
        } catch (OptimisticLockingFailureException e) {
            return error(ajax, response, redirect,
                    "Alguien cambió esa publicación mientras la tenías abierta. Se recargó "
                            + "con la versión actual.");
        } catch (AccessDeniedException | IllegalArgumentException e) {
            return error(ajax, response, redirect, e.getMessage());
        }

        if (ajax) {
            modelo.addAttribute("entrada", muroService.entradaDe(actor, publicacionId));
            modelo.addAttribute("puedeComentar", capacidades.puede(Capacidades.PORTAL_COMENTAR));
            return "muro/portal :: entrada(e=${entrada})";
        }
        if (avisoNoAjax != null) {
            redirect.addFlashAttribute("aviso", avisoNoAjax);
        }
        return "redirect:/portal";
    }

    /**
     * Un {@code null} devuelto por un método que responde {@code String} le dice a Spring
     * "ya escribí la respuesta yo mismo, no busques ninguna vista" — documentado así
     * para el caso de que el método reciba el {@code HttpServletResponse}, que es
     * justo lo que hace este. El JS lee el texto plano y lo muestra en el modal.
     */
    private String error(boolean ajax, HttpServletResponse response, RedirectAttributes redirect, String mensaje) {
        if (!ajax) {
            redirect.addFlashAttribute("error", mensaje);
            return "redirect:/portal";
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain;charset=UTF-8");
        try {
            response.getWriter().write(mensaje);
            response.getWriter().flush();
        } catch (IOException e) {
            // Si ni siquiera se puede escribir el error, no hay nada más que intentar.
        }
        return null;
    }
}
