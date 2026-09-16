package com.schoolplus.controller;

import com.schoolplus.configuration.web.Actor;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * "Mi equipo": quién le reporta directamente a un coordinador, con enlaces a las
 * pantallas donde actúa sobre cada persona (horas extra, reconocimientos, ficha).
 *
 * <p>No existía antes de los módulos de reconocimientos y ficha del colaborador —
 * {@code HorasExtraService.equipoDe} devolvía la lista pero nadie la mostraba con
 * enlaces de acción. Se construye una sola vez y ambos módulos apuntan acá.</p>
 */
@Controller
public class EquipoController {

    private final UsuarioRepository usuarioRepository;

    public EquipoController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/coordinacion/equipo")
    @PreAuthorize("@capacidades.puede('" + Capacidades.RECONOCIMIENTO_CREAR + "')"
            + " or @capacidades.puede('" + Capacidades.HORAS_EXTRA_REGISTRAR + "')"
            + " or @capacidades.puede('" + Capacidades.PERFIL_COLABORADOR_ADMINISTRAR + "')")
    @Transactional(readOnly = true)
    public String equipo(@Actor Usuario yo, Model modelo) {
        modelo.addAttribute("equipo",
                usuarioRepository.buscarEquipoConCargo(yo, EstadoUsuario.ACTIVO));
        modelo.addAttribute("seccion", "equipo");
        return "equipo/lista";
    }
}
