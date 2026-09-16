package com.schoolplus.controller;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoHorasExtra;
import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.repository.RegistroHorasExtraRepository;
import com.schoolplus.repository.SolicitudRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.cuenta.CuentaService;
import com.schoolplus.services.login.RutasPorRol;
import com.schoolplus.services.permisos.CapacidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Lo que el menú lateral y la barra superior necesitan en TODAS las pantallas internas:
 * los contadores de pendientes, a qué panel lleva el enlace "Panel", y si hay foto de
 * perfil que pintar en vez de las iniciales.
 *
 * <p>Va sobre los seis controladores del shell. Dejar fuera a alguno significaría que en
 * esa pantalla el menú pierde las píldoras de pendientes y el enlace al panel — que es
 * exactamente el tipo de inconsistencia que se nota al navegar.</p>
 */
@ControllerAdvice(assignableTypes = {
        InicioController.class,
        SolicitudController.class,
        MuroController.class,
        TableroController.class,
        TableroAreaController.class,
        AreaAdminController.class,
        CuentaController.class,
        DocumentoController.class,
        OrganigramaController.class,
        JerarquiaController.class,
        UsuarioAdminController.class,
        HorasExtraController.class,
        CapacitacionController.class,
        DocumentoInstitucionalController.class,
        ReconocimientoController.class,
        EquipoController.class,
        PerfilColaboradorController.class,
        CertificadoLaboralController.class
})
public class BandejasAdvice {

    private static final Logger log = LoggerFactory.getLogger(BandejasAdvice.class);

    private final SolicitudRepository solicitudRepository;
    private final RegistroHorasExtraRepository registroHorasExtraRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadService capacidades;
    private final CuentaService cuentaService;

    public BandejasAdvice(SolicitudRepository solicitudRepository,
                          RegistroHorasExtraRepository registroHorasExtraRepository,
                          UsuarioRepository usuarioRepository,
                          CapacidadService capacidades,
                          CuentaService cuentaService) {
        this.solicitudRepository = solicitudRepository;
        this.registroHorasExtraRepository = registroHorasExtraRepository;
        this.usuarioRepository = usuarioRepository;
        this.capacidades = capacidades;
        this.cuentaService = cuentaService;
    }

    @ModelAttribute("tieneFotoPerfil")
    @Transactional(readOnly = true)
    public boolean tieneFotoPerfil() {
        try {
            return usuarioRepository.findByEmail(emailDelActor())
                    .map(u -> cuentaService.tieneFoto(u.getId()))
                    .orElse(false);
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar la foto de perfil para el menú: {}", e.getMessage());
            return false;
        }
    }

    /**
     * A dónde apunta "Panel" en el menú: cada rol al suyo.
     *
     * <p>El destino tras el login es el portal, igual para todos; el panel del rol se
     * alcanza desde aquí.</p>
     */
    @ModelAttribute("rutaPanel")
    public String rutaPanel() {
        return RutasPorRol.destinoDe(SecurityContextHolder.getContext().getAuthentication());
    }

    @ModelAttribute("pendientesCoordinacion")
    @Transactional(readOnly = true)
    public Long pendientesCoordinacion() {
        if (!capacidades.puede(Capacidades.SOLICITUD_APROBAR_PRIMERA)) {
            return null;
        }
        return contar(() -> usuarioRepository.findByEmail(emailDelActor())
                .map(u -> solicitudRepository.countByEstadoAndCoordinadorAsignadoId(
                        EstadoSolicitud.PENDIENTE_COORDINADOR, u.getId()))
                .orElse(0L));
    }

    @ModelAttribute("pendientesRrhh")
    @Transactional(readOnly = true)
    public Long pendientesRrhh() {
        if (!capacidades.puede(Capacidades.SOLICITUD_APROBAR_SEGUNDA)) {
            return null;
        }
        return contar(() -> solicitudRepository.countByEstado(EstadoSolicitud.PENDIENTE_SUPERVISOR));
    }

    @ModelAttribute("pendientesHorasExtra")
    @Transactional(readOnly = true)
    public Long pendientesHorasExtra() {
        if (!capacidades.puede(Capacidades.HORAS_EXTRA_APROBAR)) {
            return null;
        }
        return contar(() -> registroHorasExtraRepository.countByEstado(EstadoHorasExtra.PENDIENTE));
    }

    /** Un contador del menú no puede tumbar la página que lo muestra. */
    private Long contar(java.util.function.Supplier<Long> consulta) {
        try {
            return consulta.get();
        } catch (RuntimeException e) {
            log.warn("No se pudo contar pendientes para el menú: {}", e.getMessage());
            return null;
        }
    }

    private String emailDelActor() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion == null ? null : autenticacion.getName();
    }
}
