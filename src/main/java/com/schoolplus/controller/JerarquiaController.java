package com.schoolplus.controller;

import com.schoolplus.model.Capacidades;
import com.schoolplus.services.organigrama.OrganigramaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * "Quién es jefe de quién": el árbol completo del organigrama, de solo lectura. A
 * diferencia de {@link OrganigramaController} (que edita y es solo RH/ADMIN), esta
 * pantalla también la ve COORDINADOR — necesita entender la cadena de mando aunque no
 * pueda tocarla.
 *
 * <p>Bajo {@code /mi-espacio/**} (autenticado a nivel de URL, ver {@code SecurityConfig})
 * y no {@code /talento-humano/**} ni {@code /coordinacion/**} a propósito: ningún prefijo
 * por rol cubre exactamente {ADMIN, SUPERVISOR, COORDINADOR} sin agregar USUARIO. La
 * capacidad {@code ORGANIGRAMA_VER} (V13) es la que de verdad decide quién entra.</p>
 */
@Controller
@RequestMapping("/mi-espacio/organigrama")
public class JerarquiaController {

    private final OrganigramaService organigramaService;

    public JerarquiaController(OrganigramaService organigramaService) {
        this.organigramaService = organigramaService;
    }

    @GetMapping
    @PreAuthorize("@capacidades.puede('" + Capacidades.ORGANIGRAMA_VER + "')")
    public String ver(Model modelo) {
        modelo.addAttribute("arbol", organigramaService.arbolOrganizacional());
        modelo.addAttribute("seccion", "jerarquia");
        return "organigrama/jerarquia";
    }
}
