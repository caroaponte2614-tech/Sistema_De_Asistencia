package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Tablero;
import com.schoolplus.model.TableroColumna;
import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
import com.schoolplus.services.tablero.TableroService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** La pantalla del tablero. Cada usuario ve el suyo y solo el suyo (§4.3). */
@Controller
public class TableroController {

    private final TableroService tableroService;

    public TableroController(TableroService tableroService) {
        this.tableroService = tableroService;
    }

    @GetMapping("/mi-espacio/tablero")
    @PreAuthorize("@capacidades.puede('" + Capacidades.TABLERO_USAR + "')")
    @Transactional
    public String tablero(@Actor Usuario yo, Model modelo) {
        Tablero tablero = tableroService.tableroDe(yo);

        List<TableroTarjeta> tarjetas = tableroService.tarjetasDe(tablero.getId());

        // Agrupadas por columna y en orden, para que la plantilla no ordene nada.
        Map<String, List<TableroTarjeta>> porColumna = tarjetas.stream()
                .collect(Collectors.groupingBy(t -> t.getColumna().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        modelo.addAttribute("tablero", tablero);
        modelo.addAttribute("columnas", tablero.getColumnas().stream()
                .sorted(java.util.Comparator.comparingInt(TableroColumna::getOrden))
                .toList());
        modelo.addAttribute("tarjetasPorColumna", porColumna);
        // Para marcar en rojo las vencidas sin que la plantilla llame a LocalDateTime.now()
        // por cada tarjeta: una comparación por request, no una por fila.
        modelo.addAttribute("ahora", java.time.LocalDateTime.now());
        modelo.addAttribute("seccion", "tablero");
        return "tablero/tablero";
    }
}
