package com.schoolplus.services.usuarios;

import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RolRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auth.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** El CRUD de usuarios del panel de administración. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioAdminServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;

    private UsuarioAdminService admin;

    @BeforeEach
    void crearService() {
        admin = new UsuarioAdminService(usuarioRepository, rolRepository, new ValidationService(10));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void creaConRolUsuarioPorDefecto() {
        Rol usuarioRol = new Rol();
        usuarioRol.setCodigo(Rol.USUARIO);
        when(rolRepository.findById(Rol.USUARIO)).thenReturn(Optional.of(usuarioRol));

        Usuario creado = admin.crear(datos("Pedro Pérez", "pedro@schoolplus.example", "123456"));

        assertThat(creado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(creado.getPasswordHash()).isNull();
        assertThat(creado.getRoles()).extracting(Rol::getCodigo).containsExactly(Rol.USUARIO);
    }

    @Test
    void noDejaCrearConCorreoDuplicado() {
        when(usuarioRepository.existsByEmail("pedro@schoolplus.example")).thenReturn(true);

        assertThatThrownBy(() -> admin.crear(datos("Pedro Pérez", "pedro@schoolplus.example", "123456")))
                .isInstanceOf(DatosUsuarioInvalidosException.class)
                .satisfies(e -> assertThat(((DatosUsuarioInvalidosException) e).getErrores())
                        .anyMatch(m -> m.contains("correo")));
    }

    @Test
    void actualizarPermiteConservarSuPropioCorreo() {
        Usuario existente = usuarioExistente("u-1", "pedro@schoolplus.example", "123456");
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByEmail("pedro@schoolplus.example")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByCedula("123456")).thenReturn(Optional.of(existente));

        Usuario actualizado = admin.actualizar("u-1", datos("Pedro Pérez Ruiz", "pedro@schoolplus.example", "123456"));

        assertThat(actualizado.getNombre()).isEqualTo("Pedro Pérez Ruiz");
    }

    @Test
    void actualizarRechazaElCorreoDeOtroUsuario() {
        Usuario existente = usuarioExistente("u-1", "pedro@schoolplus.example", "123456");
        Usuario otro = usuarioExistente("u-2", "ana@schoolplus.example", "654321");
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByEmail("ana@schoolplus.example")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> admin.actualizar("u-1", datos("Pedro", "ana@schoolplus.example", "123456")))
                .isInstanceOf(DatosUsuarioInvalidosException.class);
    }

    @Test
    void cambiarEstadoDesactivaSinBorrar() {
        Usuario existente = usuarioExistente("u-1", "pedro@schoolplus.example", "123456");
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(existente));

        admin.cambiarEstado("u-1", EstadoUsuario.INACTIVO);

        assertThat(existente.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
    }

    @Test
    void asignarRolesRechazaUnCodigoQueNoExiste() {
        Usuario existente = usuarioExistente("u-1", "pedro@schoolplus.example", "123456");
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(existente));
        when(rolRepository.findAllById(Set.of("INEXISTENTE"))).thenReturn(List.of());

        assertThatThrownBy(() -> admin.asignarRoles("u-1", Set.of("INEXISTENTE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarRolesReemplazaLosAnteriores() {
        Usuario existente = usuarioExistente("u-1", "pedro@schoolplus.example", "123456");
        Rol viejo = new Rol();
        viejo.setCodigo(Rol.USUARIO);
        existente.getRoles().add(viejo);

        Rol coordinador = new Rol();
        coordinador.setCodigo(Rol.COORDINADOR);
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(existente));
        when(rolRepository.findAllById(Set.of(Rol.COORDINADOR))).thenReturn(List.of(coordinador));

        admin.asignarRoles("u-1", Set.of(Rol.COORDINADOR));

        assertThat(existente.getRoles()).containsExactly(coordinador);
    }

    private DatosUsuario datos(String nombre, String email, String cedula) {
        return new DatosUsuario(nombre, email, cedula, "3001234567", null);
    }

    private Usuario usuarioExistente(String id, String email, String cedula) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Nombre viejo");
        usuario.setEmail(email);
        usuario.setCedula(cedula);
        usuario.setTelefono("3000000000");
        usuario.setEstado(EstadoUsuario.ACTIVO);
        return usuario;
    }
}
