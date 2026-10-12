package com.schoolplus.services.permisos;

import com.schoolplus.dto.CapacidadConcedida;
import com.schoolplus.model.Ambito;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RolCapacidadRepository;
import com.schoolplus.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * La matriz de permisos (§3.1), que el §5.4 marca como test obligatorio.
 *
 * <p>Lo que se comprueba no es que la tabla exista, sino que <em>decide</em>: alcance,
 * precedencia entre roles acumulados y, sobre todo, que tener una capacidad no da acceso
 * a los datos de cualquiera.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapacidadServiceTest {

    private static final String ID_ACTOR = "11111111-1111-1111-1111-111111111111";
    private static final String ID_AJENO = "33333333-3333-3333-3333-333333333333";

    @Mock
    private RolCapacidadRepository rolCapacidadRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private CapacidadService capacidades;

    @BeforeEach
    void prepararActorAutenticado() {
        capacidades = new CapacidadService(rolCapacidadRepository, usuarioRepository);

        Usuario actor = usuario(ID_ACTOR, "coordinador@schoolplus.example", Rol.COORDINADOR);
        when(usuarioRepository.findByEmail("coordinador@schoolplus.example")).thenReturn(Optional.of(actor));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("coordinador@schoolplus.example", "n/a", List.of()));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concedeLoQueLaMatrizConcede() {
        conceder(new CapacidadConcedida(Capacidades.ASISTENCIA_TOMAR, "Tomar asistencia", "ASISTENCIA", Ambito.PROPIO));

        assertThat(capacidades.puede(Capacidades.ASISTENCIA_TOMAR)).isTrue();
        assertThat(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)).contains(Ambito.PROPIO);
    }

    @Test
    void niegaLoQueNoEsta() {
        conceder(new CapacidadConcedida(Capacidades.ASISTENCIA_TOMAR, "Tomar asistencia", "ASISTENCIA", Ambito.PROPIO));

        // Una fila apagada por pendiente_confirmacion no la devuelve la consulta, así
        // que aquí se ve exactamente igual que si no existiera: negada.
        assertThat(capacidades.puede(Capacidades.CLASE_ADMINISTRAR)).isFalse();
        assertThat(capacidades.ambitoDe(Capacidades.CLASE_ADMINISTRAR)).isEmpty();
    }

    @Test
    void quienAcumulaRolesSeQuedaConElAmbitoMayor() {
        conceder(
                new CapacidadConcedida(Capacidades.ASISTENCIA_REPORTE, "Ver reportes", "ASISTENCIA", Ambito.PROPIO),
                new CapacidadConcedida(Capacidades.ASISTENCIA_REPORTE, "Ver reportes", "ASISTENCIA", Ambito.TODOS));

        assertThat(capacidades.ambitoDe(Capacidades.ASISTENCIA_REPORTE)).contains(Ambito.TODOS);
    }

    @Test
    void elAmbitoPropioNoAlcanzaAOtraPersona() {
        conceder(new CapacidadConcedida(Capacidades.ASISTENCIA_REPORTE, "Ver reportes", "ASISTENCIA", Ambito.PROPIO));

        assertThat(capacidades.puedeSobre(Capacidades.ASISTENCIA_REPORTE, ID_ACTOR)).isTrue();
        assertThat(capacidades.puedeSobre(Capacidades.ASISTENCIA_REPORTE, ID_AJENO)).isFalse();
    }

    @Test
    void elAmbitoTodosAlcanzaACualquiera() {
        conceder(new CapacidadConcedida(Capacidades.ASISTENCIA_REPORTE, "Ver reportes", "ASISTENCIA", Ambito.TODOS));

        assertThat(capacidades.puedeSobre(Capacidades.ASISTENCIA_REPORTE, ID_AJENO)).isTrue();
    }

    @Test
    void sinSesionNoHayNingunaCapacidad() {
        SecurityContextHolder.clearContext();
        conceder(new CapacidadConcedida(Capacidades.USUARIO_ADMINISTRAR, "Administrar", "ADMINISTRACION", Ambito.TODOS));

        assertThat(capacidades.capacidadesDelActor()).isEmpty();
        assertThat(capacidades.puede(Capacidades.USUARIO_ADMINISTRAR)).isFalse();
        assertThat(capacidades.puedeSobre(Capacidades.USUARIO_ADMINISTRAR, ID_AJENO)).isFalse();
    }

    @Test
    void unUsuarioSinRolesNoConsultaLaMatriz() {
        Usuario sinRoles = new Usuario();
        sinRoles.setId(ID_ACTOR);
        sinRoles.setEmail("coordinador@schoolplus.example");
        when(usuarioRepository.findByEmail("coordinador@schoolplus.example")).thenReturn(Optional.of(sinRoles));

        assertThat(capacidades.capacidadesDelActor()).isEmpty();
    }

    private void conceder(CapacidadConcedida... concedidas) {
        when(rolCapacidadRepository.capacidadesDeRoles(anyCollection())).thenReturn(List.of(concedidas));
    }

    private Usuario usuario(String id, String email, String rolCodigo) {
        Rol rol = new Rol();
        rol.setCodigo(rolCodigo);
        rol.setNombre(rolCodigo);

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.getRoles().add(rol);
        return usuario;
    }
}
