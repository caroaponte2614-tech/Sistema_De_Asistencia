package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.PrioridadTarea;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.tablero.TableroReporteService;
import com.schoolplus.services.tablero.TableroService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * API del tablero de área: las mismas acciones que {@link TableroApiController}, pero
 * operando sobre el tablero compartido de un área en vez del personal. {@link TableroService}
 * valida en cada llamada que quien pide sea el líder de esa área o un administrador (§
 * "solo el líder edita, el resto del área solo ve").
 */
@RestController
@RequestMapping("/api/v1/tablero/area/{areaId}")
@PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_USAR + "')")
public class TableroAreaApiController {

    private final TableroService tableroService;
    private final TableroReporteService reporteService;

    public TableroAreaApiController(TableroService tableroService, TableroReporteService reporteService) {
        this.tableroService = tableroService;
        this.reporteService = reporteService;
    }

    public record TarjetaRequest(String columnaId, String titulo, String etiqueta, String nota,
                                 PrioridadTarea prioridad, LocalDateTime fechaLimite, Long version) {
    }

    public record MovimientoRequest(String columnaDestinoId, Integer posicion, Long version) {
    }

    @PostMapping("/tarjetas")
    public TableroApiController.TarjetaResponse crear(@PathVariable String areaId,
                                                       @RequestBody TarjetaRequest peticion,
                                                       @Actor Usuario actor) {
        return TableroApiController.TarjetaResponse.de(tableroService.crearTarjetaDeArea(
                actor, areaId, peticion.columnaId(), peticion.titulo(),
                peticion.etiqueta(), peticion.nota(), peticion.prioridad(), peticion.fechaLimite()));
    }

    @PutMapping("/tarjetas/{id}")
    public TableroApiController.TarjetaResponse actualizar(@PathVariable String areaId,
                                                            @PathVariable String id,
                                                            @RequestBody TarjetaRequest peticion,
                                                            @Actor Usuario actor) {
        return TableroApiController.TarjetaResponse.de(tableroService.actualizarTarjetaDeArea(
                actor, areaId, id, version(peticion.version()), peticion.titulo(),
                peticion.etiqueta(), peticion.nota(), peticion.prioridad(), peticion.fechaLimite()));
    }

    @PatchMapping("/tarjetas/{id}/mover")
    public TableroApiController.TarjetaResponse mover(@PathVariable String areaId,
                                                       @PathVariable String id,
                                                       @RequestBody MovimientoRequest peticion,
                                                       @Actor Usuario actor) {
        return TableroApiController.TarjetaResponse.de(tableroService.moverTarjetaDeArea(
                actor, areaId, id, version(peticion.version()),
                peticion.columnaDestinoId(),
                peticion.posicion() == null ? 0 : peticion.posicion()));
    }

    @DeleteMapping("/tarjetas/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable String areaId,
                                                         @PathVariable String id,
                                                         @Actor Usuario actor) {
        tableroService.eliminarTarjetaDeArea(actor, areaId, id);
        return ResponseEntity.ok(Map.of("estado", "eliminada"));
    }

    @GetMapping("/reporte.xlsx")
    public ResponseEntity<byte[]> reporte(@PathVariable String areaId, @Actor Usuario actor) {
        byte[] libro = reporteService.generarReporteDeArea(actor, areaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tablero-area.xlsx\"")
                .body(libro);
    }

    private long version(Long version) {
        return version == null ? -1L : version;
    }
}
