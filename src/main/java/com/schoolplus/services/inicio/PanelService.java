package com.schoolplus.services.inicio;

import com.schoolplus.dto.UsuarioResumen;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos del panel de aterrizaje.
 *
 * <p>Existe para resolver dentro de la transacción todo lo que la plantilla necesita:
 * {@code spring.jpa.open-in-view} está apagado, así que la vista no puede tirar de
 * relaciones perezosas.</p>
 *
 * <p>Las capacidades NO se consultan aquí: las sirve
 * {@code services.permisos.CapacidadService}, que es el único sitio que interroga la
 * matriz. Tenerlas en dos sitios era garantía de que un día divergieran.</p>
 */
@Service
public class PanelService {

    private static final String SIN_ASIGNAR = "Sin asignar";

    private final UsuarioRepository usuarioRepository;

    public PanelService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public UsuarioResumen resumenDe(String email) {
        Usuario usuario = usuarioRepository.buscarConSedeYCargoPorEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        return new UsuarioResumen(
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getSede() == null ? SIN_ASIGNAR : usuario.getSede().getNombre(),
                usuario.getCargo() == null ? SIN_ASIGNAR : usuario.getCargo().getNombre());
    }
}
