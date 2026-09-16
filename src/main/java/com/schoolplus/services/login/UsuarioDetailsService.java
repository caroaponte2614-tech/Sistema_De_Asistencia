package com.schoolplus.services.login;

import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga la identidad para Spring Security.
 *
 * <p>Origen: un proyecto de referencia (services/login/UsuarioDetailsService.java). Cambia en que los
 * roles son una colección y no un {@code enum} de un solo valor: una persona puede ser
 * COORDINADOR y además USUARIO.</p>
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioDetailsService.class);

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String correo = email == null ? "" : email.trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByEmail(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Un usuario migrado sin hash utilizable (§6.2) no puede entrar con contraseña:
        // su camino es "recordar contraseña". Se responde igual que si no existiera, para
        // no distinguir un caso del otro desde fuera.
        if (!usuario.tienePasswordUtilizable()) {
            log.info("Intento de login de {} sin contraseña utilizable (cuenta migrada).", correo);
            throw new UsernameNotFoundException("Usuario sin contraseña utilizable");
        }

        List<SimpleGrantedAuthority> autoridades = usuario.getRoles().stream()
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.getCodigo()))
                .toList();

        return User.withUsername(usuario.getEmail())
                .password(usuario.getPasswordHash())
                .authorities(autoridades)
                .disabled(!usuario.estaActivo())
                .build();
    }
}
