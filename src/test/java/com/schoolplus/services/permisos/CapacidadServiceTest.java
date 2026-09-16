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
 * La matriz de permisos (§3.1), que el §5.4 marca como test obligatorio junto con la
 * máquina de estados.
 *
 * <p>Lo que se comprueba no es que la tabla exista, sino que <em>decide</em>: alcance,
 * precedencia entre roles acumulados y, sobre todo, que tener una capacidad no da acceso
 * a los datos de cualquiera.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapacidadServiceTest {

    private static final String ID_ACTOR = "11111111-1111-1111-1111-111111111111";
    private static final String ID_SUBORDINADO = "22222222-2222-2222-2222-222222222222";
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
        conceder(new CapacidadConcedida(Capacidades.SOLICITUD_APROBAR_PRIMERA, "Aprobar", "SOLICITUDES", Ambito.AREA));

        assertThat(capacidades.puede(Capacidades.SOLICITUD_APROBAR_PRIMERA)).isTrue();
        assertThat(capacidades.ambitoDe(Capacidades.SOLICITUD_APROBAR_PRIMERA)).contains(Ambito.AREA);
    }

    @Test
    void niegaLoQueNoEsta() {
        conceder(new CapacidadConcedida(Capacidades.SOLICITUD_CREAR, "Crear", "SOLICITUDES", Ambito.PROPIO));

        // Una fila apagada por pendiente_confirmacion no la devuelve la consulta, así
        // que aquí se ve exactamente igual que si no existiera: negada.
        assertThat(capacidades.puede(Capacidades.REPORTE_GENERAR)).isFalse();
        assertThat(capacidades.ambitoDe(Capacidades.REPORTE_GENERAR)).isEmpty();
    }

    @Test
    void quienAcumulaRolesSeQuedaConElAmbitoMayor() {
        conceder(
                new CapacidadConcedida(Capacidades.TABLERO_VER, "Ver tableros", "TABLERO", Ambito.AREA),
                new CapacidadConcedida(Capacidades.TABLERO_VER, "Ver tableros", "TABLERO", Ambito.TODOS));

        assertThat(capacidades.ambitoDe(Capacidades.TABLERO_VER)).contains(Ambito.TODOS);
    }

    @Test
    void elAmbitoPropioNoAlcanzaAOtraPersona() {
        conceder(new CapacidadConcedida(Capacidades.DOCUMENTO_RESTRINGIDO_VER, "Ver", "DOCUMENTOS", Ambito.PROPIO));

        assertThat(capacidades.puedeSobre(Capacidades.DOCUMENTO_RESTRINGIDO_VER, ID_ACTOR)).isTrue();
        assertThat(capacidades.puedeSobre(Capacidades.DOCUMENTO_RESTRINGIDO_VER, ID_AJENO)).isFalse();
    }

    @Test
    void elAmbitoAreaSoloAlcanzaAQuienMeTieneDeCoordinador() {
        conceder(new CapacidadConcedida(Capacidades.HORAS_EXTRA_REGISTRAR, "Horas extra", "HORAS_EXTRA", Ambito.AREA));

        Usuario subordinado = usuario(ID_SUBORDINADO, "colaborador@schoolplus.example", Rol.USUARIO);
        subordinado.setCoordinador(usuario(ID_ACTOR, "coordinador@schoolplus.example", Rol.COORDINADOR));
        when(usuarioRepository.findById(ID_SUBORDINADO)).thenReturn(Optional.of(subordinado));

        // Mismo rol, pero cuelga de otro coordinador: fuera de alcance.
        Usuario ajeno = usuario(ID_AJENO, "otro@schoolplus.example", Rol.USUARIO);
        ajeno.setCoordinador(usuario("99999999-9999-9999-9999-999999999999", "jefa@schoolplus.example", Rol.COORDINADOR));
        when(usuarioRepository.findById(ID_AJENO)).thenReturn(Optional.of(ajeno));

        assertThat(capacidades.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, ID_SUBORDINADO)).isTrue();
        assertThat(capacidades.puedeSobre(Capacidades.HORAS_EXTRA_REGISTRAR, ID_AJENO)).isFalse();
    }

    @Test
    void elAmbitoTodosAlcanzaACualquiera() {
        conceder(new CapacidadConcedida(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR, "Subir", "DOCUMENTOS", Ambito.TODOS));

        assertThat(capacidades.puedeSobre(Capacidades.DOCUMENTO_RESTRINGIDO_SUBIR, ID_AJENO)).isTrue();
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
