package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Usuario;
import com.schoolplus.model.UsuarioFoto;
import com.schoolplus.services.cuenta.CuentaService;
import com.schoolplus.services.inicio.PanelService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Mi cuenta": lo que cualquiera de los cuatro roles puede ver y cambiar de sí mismo,
 * empezando por la foto de perfil que se pinta en la barra superior del shell.
 *
 * <p>Sin {@code @PreAuthorize}: no hay una capacidad de la matriz que gobierne esto —cada
 * persona administra su propia foto, no la ajena. La única puerta es estar autenticado,
 * y esa ya la pone {@code SecurityConfig} con {@code anyRequest().authenticated()}.</p>
 */
@Controller
public class CuentaController {

    private final CuentaService cuentaService;
    private final PanelService panelService;

    public CuentaController(CuentaService cuentaService, PanelService panelService) {
        this.cuentaService = cuentaService;
        this.panelService = panelService;
    }

    @GetMapping("/mi-cuenta")
    public String miCuenta(@Actor Usuario actor, Model modelo) {
        modelo.addAttribute("usuario", panelService.resumenDe(actor.getEmail()));
        modelo.addAttribute("tieneFoto", cuentaService.tieneFoto(actor.getId()));
        modelo.addAttribute("seccion", "cuenta");
        return "cuenta/mi-cuenta";
    }

    @PostMapping("/mi-cuenta/foto")
    public String subirFoto(@Actor Usuario actor,
                            @RequestParam("archivo") MultipartFile archivo,
                            RedirectAttributes redirect) {
        try {
            cuentaService.subirFoto(actor.getId(), archivo);
            redirect.addFlashAttribute("aviso", "Foto de perfil actualizada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-cuenta";
    }

    @PostMapping("/mi-cuenta/foto/eliminar")
    public String eliminarFoto(@Actor Usuario actor, RedirectAttributes redirect) {
        cuentaService.eliminarFoto(actor.getId());
        redirect.addFlashAttribute("aviso", "Foto de perfil eliminada.");
        return "redirect:/mi-cuenta";
    }

    /**
     * Sirve la foto del usuario autenticado. Sin cache larga: quien acaba de cambiarla
     * espera verla distinta de inmediato, no la versión vieja del navegador.
     */
    @GetMapping("/mi-cuenta/foto")
    public ResponseEntity<byte[]> foto(@Actor Usuario actor) {
        UsuarioFoto foto = cuentaService.obtenerFoto(actor.getId()).orElse(null);
        if (foto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
                .cacheControl(CacheControl.noCache())
                .body(foto.getContenido());
    }
}
