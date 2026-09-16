package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.PrioridadTarea;
import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.tablero.TableroService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API del tablero. La consume el JavaScript de la pantalla.
 *
 * <p>Origen de la forma: un proyecto de referencia (controller/TableroController.java), que ya era
 * REST + arrastrar y soltar. Cambia que aquí cada llamada opera sobre el tablero de QUIEN
 * llama, y que todas las que modifican exigen la versión que el cliente tenía a la vista.</p>
 *
 * <p>Los conflictos los traduce {@code ManejadorDeConcurrencia} a <strong>409</strong>
 * (§5.2), y el JS recarga el tablero cuando lo recibe.</p>
 */
@RestController
@RequestMapping("/api/v1/tablero")
@PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_USAR + "')")
public class TableroApiController {

    private final TableroService tableroService;

    public TableroApiController(TableroService tableroService) {
        this.tableroService = tableroService;
    }

    public record TarjetaRequest(String columnaId, String titulo, String etiqueta, String nota,
                                 PrioridadTarea prioridad, LocalDateTime fechaLimite, Long version) {
    }

    public record MovimientoRequest(String columnaDestinoId, Integer posicion, Long version) {
    }

    /** Lo que el JS necesita para repintar una tarjeta, con su versión al día. */
    public record TarjetaResponse(String id, String columnaId, String titulo, String etiqueta,
                                  String nota, String prioridad, LocalDateTime fechaLimite,
                                  LocalDateTime creadoEn, int orden, long version) {

        static TarjetaResponse de(TableroTarjeta t) {
            return new TarjetaResponse(t.getId(), t.getColumna().getId(), t.getTitulo(),
                    t.getEtiqueta(), t.getNota(), t.getPrioridad().name(), t.getFechaLimite(),
                    t.getCreadoEn(), t.getOrden(), t.getVersion());
        }
    }

    @PostMapping("/tarjetas")
    public TarjetaResponse crear(@RequestBody TarjetaRequest peticion, @Actor Usuario actor) {
        return TarjetaResponse.de(tableroService.crearTarjeta(
                actor, peticion.columnaId(), peticion.titulo(),
                peticion.etiqueta(), peticion.nota(), peticion.prioridad(), peticion.fechaLimite()));
    }

    @PutMapping("/tarjetas/{id}")
    public TarjetaResponse actualizar(@PathVariable String id,
                                      @RequestBody TarjetaRequest peticion,
                                      @Actor Usuario actor) {
        return TarjetaResponse.de(tableroService.actualizarTarjeta(
                actor, id, version(peticion.version()), peticion.titulo(),
                peticion.etiqueta(), peticion.nota(), peticion.prioridad(), peticion.fechaLimite()));
    }

    /** Arrastrar y soltar: columna destino + posición dentro de ella. */
    @PatchMapping("/tarjetas/{id}/mover")
    public TarjetaResponse mover(@PathVariable String id,
                                 @RequestBody MovimientoRequest peticion,
                                 @Actor Usuario actor) {
        return TarjetaResponse.de(tableroService.moverTarjeta(
                actor, id, version(peticion.version()),
                peticion.columnaDestinoId(),
                peticion.posicion() == null ? 0 : peticion.posicion()));
    }

    @DeleteMapping("/tarjetas/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable String id, @Actor Usuario actor) {
        tableroService.eliminarTarjeta(actor, id);
        return ResponseEntity.ok(Map.of("estado", "eliminada"));
    }

    /** Sin versión en la petición no se comprueba nada; -1 lo indica. */
    private long version(Long version) {
        return version == null ? -1L : version;
    }
}
