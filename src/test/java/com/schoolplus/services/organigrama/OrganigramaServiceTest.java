package com.schoolplus.services.organigrama;

import com.schoolplus.model.Cargo;
import com.schoolplus.model.JefeCargo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CargoRepository;
import com.schoolplus.repository.JefeCargoRepository;
import com.schoolplus.repository.UsuarioRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El organigrama que RH administra (D-017): la sugerencia por cargo y, sobre todo, que
 * no se pueda cerrar un ciclo de mando al reasignar un jefe a mano.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganigramaServiceTest {

    @Mock
    private JefeCargoRepository jefeCargoRepository;
    @Mock
    private CargoRepository cargoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private OrganigramaService organigrama;

    @BeforeEach
    void crearService() {
        organigrama = new OrganigramaService(jefeCargoRepository, cargoRepository, usuarioRepository);
    }

    @Test
    void sugiereElJefeDelCargoActual() {
        Cargo mensajero = cargo("cargo-mensajero", "MENSAJERO");
        Usuario colaborador = usuario("col-1", "Pedro", null);
        colaborador.setCargo(mensajero);

        Usuario juan = usuario("jefe-1", "Juan Pérez", null);
        JefeCargo asignacion = new JefeCargo();
        asignacion.setCargoId(mensajero.getId());
        asignacion.setCargo(mensajero);
        asignacion.setJefe(juan);
        when(jefeCargoRepository.findById(mensajero.getId())).thenReturn(Optional.of(asignacion));

        assertThat(organigrama.jefeSugeridoPara(colaborador)).contains(juan);
    }

    @Test
    void sinCargoNoHaySugerencia() {
        Usuario colaborador = usuario("col-1", "Pedro", null);
        assertThat(organigrama.jefeSugeridoPara(colaborador)).isEmpty();
    }

    @Test
    void asignarCargosLiberaLosQueYaNoEstanYUpsertaLosNuevos() {
        Usuario juan = usuario("jefe-1", "Juan Pérez", null);
        Cargo mensajero = cargo("cargo-mensajero", "MENSAJERO");
        when(usuarioRepository.findById("jefe-1")).thenReturn(Optional.of(juan));
        when(cargoRepository.findById("cargo-mensajero")).thenReturn(Optional.of(mensajero));
        when(jefeCargoRepository.findById("cargo-mensajero")).thenReturn(Optional.empty());

        organigrama.asignarCargos("jefe-1", Set.of("cargo-mensajero"));

        verify(jefeCargoRepository).deleteByJefeIdAndCargoIdNotIn("jefe-1", List.of("cargo-mensajero"));
        verify(jefeCargoRepository).save(any(JefeCargo.class));
    }

    @Test
    void unColaboradorNoPuedeSerSuPropioJefe() {
        Usuario pedro = usuario("col-1", "Pedro", null);

        assertThatThrownBy(() -> organigrama.reasignarJefe(pedro, pedro))
                .isInstanceOf(CicloDeOrganigramaException.class);
    }

    @Test
    void noSePuedeAsignarComoJefeAQuienYaEstaAMisOrdenes() {
        // Juan es jefe de Pedro. Intentar que Pedro sea jefe de Juan cerraría el ciclo.
        Usuario juan = usuario("jefe-1", "Juan", null);
        Usuario pedro = usuario("col-1", "Pedro", juan);
        when(usuarioRepository.findById("jefe-1")).thenReturn(Optional.of(juan));

        assertThatThrownBy(() -> organigrama.reasignarJefe(juan, pedro))
                .isInstanceOf(CicloDeOrganigramaException.class);
    }

    @Test
    void reasignaSinProblemaCuandoNoHayCiclo() {
        Usuario ana = usuario("jefe-2", "Ana", null);
        Usuario pedro = usuario("col-1", "Pedro", null);

        organigrama.reasignarJefe(pedro, ana);

        assertThat(pedro.getCoordinador()).isEqualTo(ana);
        verify(usuarioRepository).save(pedro);
    }

    @Test
    void quitarElJefeDejaAlColaboradorSinCoordinador() {
        Usuario ana = usuario("jefe-2", "Ana", null);
        Usuario pedro = usuario("col-1", "Pedro", ana);

        organigrama.reasignarJefe(pedro, null);

        assertThat(pedro.getCoordinador()).isNull();
    }

    @Test
    void esExcepcionCuandoElJefeActualNoCoincideConElSugerido() {
        Cargo mensajero = cargo("cargo-mensajero", "MENSAJERO");
        Usuario juan = usuario("jefe-1", "Juan", null);
        Usuario otro = usuario("jefe-2", "Otro", null);
        Usuario pedro = usuario("col-1", "Pedro", otro);
        pedro.setCargo(mensajero);

        JefeCargo sugerencia = new JefeCargo();
        sugerencia.setCargoId(mensajero.getId());
        sugerencia.setCargo(mensajero);
        sugerencia.setJefe(juan);
        when(jefeCargoRepository.findAllConCargoYJefeOrderByCargo_NombreAsc()).thenReturn(List.of(sugerencia));
        when(usuarioRepository.buscarActivosConCargoYCoordinador(any())).thenReturn(List.of(pedro));

        List<ExcepcionManual> excepciones = organigrama.excepcionesManuales();

        assertThat(excepciones).hasSize(1);
        assertThat(excepciones.get(0).colaborador()).isEqualTo(pedro);
        assertThat(excepciones.get(0).jefeSugerido()).isEqualTo(juan);
    }

    @Test
    void noEsExcepcionCuandoElJefeActualCoincideConElSugerido() {
        Cargo mensajero = cargo("cargo-mensajero", "MENSAJERO");
        Usuario juan = usuario("jefe-1", "Juan", null);
        Usuario pedro = usuario("col-1", "Pedro", juan);
        pedro.setCargo(mensajero);

        JefeCargo sugerencia = new JefeCargo();
        sugerencia.setCargoId(mensajero.getId());
        sugerencia.setCargo(mensajero);
        sugerencia.setJefe(juan);
        when(jefeCargoRepository.findAllConCargoYJefeOrderByCargo_NombreAsc()).thenReturn(List.of(sugerencia));
        when(usuarioRepository.buscarActivosConCargoYCoordinador(any())).thenReturn(List.of(pedro));

        assertThat(organigrama.excepcionesManuales()).isEmpty();
    }

    @Test
    void esExcepcionTenerJefeCuandoElCargoNoSugiereNinguno() {
        Usuario ana = usuario("jefe-2", "Ana", null);
        Usuario pedro = usuario("col-1", "Pedro", ana);
        when(jefeCargoRepository.findAllConCargoYJefeOrderByCargo_NombreAsc()).thenReturn(List.of());
        when(usuarioRepository.buscarActivosConCargoYCoordinador(any())).thenReturn(List.of(pedro));

        List<ExcepcionManual> excepciones = organigrama.excepcionesManuales();

        assertThat(excepciones).hasSize(1);
        assertThat(excepciones.get(0).jefeSugerido()).isNull();
    }

    @Test
    void noEsExcepcionSinJefeYSinSugerencia() {
        Usuario pedro = usuario("col-1", "Pedro", null);
        when(jefeCargoRepository.findAllConCargoYJefeOrderByCargo_NombreAsc()).thenReturn(List.of());
        when(usuarioRepository.buscarActivosConCargoYCoordinador(any())).thenReturn(List.of(pedro));

        assertThat(organigrama.excepcionesManuales()).isEmpty();
    }

    private Cargo cargo(String id, String nombre) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setNombre(nombre);
        return cargo;
    }

    private Usuario usuario(String id, String nombre, Usuario coordinador) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre(nombre);
        usuario.setCoordinador(coordinador);
        return usuario;
    }
}
