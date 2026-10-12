package com.schoolplus.services.inicio;

import com.schoolplus.dto.UsuarioResumen;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Datos del panel de aterrizaje.
 *
 * <p>Las capacidades NO se consultan aquí: las sirve
 * {@code services.permisos.CapacidadService}, que es el único sitio que interroga la
 * matriz. Tenerlas en dos sitios era garantía de que un día divergieran.</p>
 */
@Service
public class PanelService {

    private final UsuarioRepository usuarioRepository;

    public PanelService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public UsuarioResumen resumenDe(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        return new UsuarioResumen(
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRoles().stream()
                        .map(Rol::getNombre)
                        .sorted()
                        .collect(Collectors.joining(", ")));
    }
}
