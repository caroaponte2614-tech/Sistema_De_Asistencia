package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.notificaciones.NotificacionPayload;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * La API que consume la campanita del shell. Sin {@code @PreAuthorize}: no hay una
 * capacidad de la matriz que gobierne "ver mis propias notificaciones" — la única
 * puerta es estar autenticado, y cada consulta ya está acotada al actor por
 * construcción (nunca recibe un {@code usuarioId} del cliente).
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionApiController {

    private static final int CANTIDAD_RECIENTES = 30;

    private final NotificacionService notificacionService;

    public NotificacionApiController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    public record ListaNotificaciones(List<NotificacionPayload> recientes, long noLeidas) {
    }

    @GetMapping
    public ListaNotificaciones listar(@Actor Usuario actor) {
        List<NotificacionPayload> recientes = notificacionService.recientesDe(actor.getId(), CANTIDAD_RECIENTES)
                .map(NotificacionPayload::de)
                .getContent();
        return new ListaNotificaciones(recientes, notificacionService.noLeidasDe(actor.getId()));
    }

    @PostMapping("/{id}/leer")
    public Map<String, String> marcarLeida(@PathVariable String id, @Actor Usuario actor) {
        notificacionService.marcarLeida(actor.getId(), id);
        return Map.of("estado", "leida");
    }

    @PostMapping("/leer-todas")
    public Map<String, String> marcarTodasLeidas(@Actor Usuario actor) {
        notificacionService.marcarTodasLeidas(actor.getId());
        return Map.of("estado", "leidas");
    }
}
