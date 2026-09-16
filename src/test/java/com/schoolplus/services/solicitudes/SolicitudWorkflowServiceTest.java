package com.schoolplus.services.solicitudes;

import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.model.FamiliaSolicitud;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Solicitud;
import com.schoolplus.model.SolicitudHistorial;
import com.schoolplus.model.SolicitudTipo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.SolicitudHistorialRepository;
import com.schoolplus.repository.SolicitudRepository;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.services.vacaciones.VacacionesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El motor de aprobación, regla por regla del §4.4.
 *
 * <p>Es el test que el §5.4 declara obligatorio: "unitarios de la máquina de estados de
 * aprobaciones... es la lógica más delicada".</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitudWorkflowServiceTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 11);

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private SolicitudHistorialRepository historialRepository;
    @Mock
    private VacacionesService vacacionesService;
    @Mock
    private DocumentoAlmacenamiento almacenamiento;
    @Mock
    private CapacidadService capacidadService;

    private SolicitudWorkflowService workflow;

    private Usuario colaborador;
    private Usuario coordinador;
    private Usuario rrhh;

    @BeforeEach
    void preparar() {
        workflow = new SolicitudWorkflowService(
                solicitudRepository, historialRepository, vacacionesService,
                new DiasHabilesService("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"),
                almacenamiento, capacidadService,
                true);

        coordinador = usuario("coord-1", "coordinador@schoolplus.example", Rol.COORDINADOR);
        rrhh = usuario("rrhh-1", "rrhh@schoolplus.example", Rol.SUPERVISOR);
        colaborador = usuario("colab-1", "colaborador@schoolplus.example", Rol.USUARIO);
        colaborador.setCoordinador(coordinador);

        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Creación y envío ─────────────────────────────────────────────────────

    @Test
    void alCrearSeCalculanLosDiasHabilesYSeCongelaElCoordinador() {
        Solicitud s = workflow.crearBorrador(colaborador, tipoPermiso(), LUNES, VIERNES, "Trámite");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.BORRADOR);
        assertThat(s.getDiasHabiles()).isEqualByComparingTo("5");
        assertThat(s.getCoordinadorAsignado()).isSameAs(coordinador);
    }

    @Test
    void unRangoSinDiasHabilesSeRechaza() {
        LocalDate sabado = LocalDate.of(2026, 9, 12);
        LocalDate domingo = LocalDate.of(2026, 9, 13);

        assertThatThrownBy(() -> workflow.crearBorrador(colaborador, tipoPermiso(), sabado, domingo, null))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("ningún día hábil");
    }

    @Test
    void solamenteElSolicitantePuedeEnviarSuSolicitud() {
        Solicitud s = borradorGuardado();

        assertThatThrownBy(() -> workflow.enviar(s.getId(), coordinador))
                .isInstanceOf(NoAutorizadoADecidirException.class);
    }

    @Test
    void alEnviarEntraEnLaBandejaDelCoordinador() {
        Solicitud s = borradorGuardado();

        workflow.enviar(s.getId(), colaborador);

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE_COORDINADOR);
    }

    @Test
    void elCoordinadorQuePidePermisoEntraDirectoATalentoHumano() {
        // Regla del §4.4 que el prompt pide confirmar; aquí está tras un flag.
        Usuario jefe = usuario("jefe-1", "jefa@schoolplus.example", Rol.COORDINADOR);
        coordinador.setCoordinador(jefe);

        Solicitud s = workflow.crearBorrador(coordinador, tipoPermiso(), LUNES, VIERNES, null);
        assertThat(s.getCoordinadorAsignado()).isNull();

        when(solicitudRepository.buscarCompleta(s.getId())).thenReturn(Optional.of(s));
        workflow.enviar(s.getId(), coordinador);

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE_SUPERVISOR);
    }

    @Test
    void conElFlagApagadoElCoordinadorSiPasaPorSuPropioJefe() {
        SolicitudWorkflowService sinSalto = new SolicitudWorkflowService(
                solicitudRepository, historialRepository, vacacionesService,
                new DiasHabilesService("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"),
                almacenamiento, capacidadService,
                false);
        Usuario jefe = usuario("jefe-1", "jefa@schoolplus.example", Rol.COORDINADOR);
        coordinador.setCoordinador(jefe);

        Solicitud s = sinSalto.crearBorrador(coordinador, tipoPermiso(), LUNES, VIERNES, null);

        assertThat(s.getCoordinadorAsignado()).isSameAs(jefe);
    }

    @Test
    void nadieSeApruebaASiMismoNiPorUnOrganigramaMalCargado() {
        colaborador.setCoordinador(colaborador);

        Solicitud s = workflow.crearBorrador(colaborador, tipoPermiso(), LUNES, VIERNES, null);

        assertThat(s.getCoordinadorAsignado()).isNull();
    }

    // ── Primera instancia ────────────────────────────────────────────────────

    @Test
    void soloElCoordinadorAsignadoDecideEnPrimeraInstancia() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);
        Usuario otroCoordinador = usuario("coord-2", "otro@schoolplus.example", Rol.COORDINADOR);

        assertThatThrownBy(() -> workflow.aprobarCoordinador(s.getId(), otroCoordinador, "vale"))
                .isInstanceOf(NoAutorizadoADecidirException.class)
                .hasMessageContaining("no tú");
    }

    @Test
    void elSolicitanteNoPuedeAprobarSuPropiaSolicitud() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);
        s.setCoordinadorAsignado(colaborador);

        assertThatThrownBy(() -> workflow.aprobarCoordinador(s.getId(), colaborador, "vale"))
                .isInstanceOf(NoAutorizadoADecidirException.class)
                .hasMessageContaining("tu propia solicitud");
    }

    @Test
    void aprobarEnPrimeraInstanciaLoPasaATalentoHumano() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);

        workflow.aprobarCoordinador(s.getId(), coordinador, "De acuerdo");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE_SUPERVISOR);
        assertThat(s.getDecididoCoordPor()).isSameAs(coordinador);
        assertThat(s.getDecididoCoordEn()).isNotNull();
    }

    @Test
    void denegarSinMotivoNoSePermite() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);

        assertThatThrownBy(() -> workflow.rechazarCoordinador(s.getId(), coordinador, "   "))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void siElCoordinadorDeniegaElTramiteTerminaYNoLlegaARrhh() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);

        workflow.rechazarCoordinador(s.getId(), coordinador, "No hay cobertura esa semana");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADO_COORD);
        assertThat(s.getEstado().esFinal()).isTrue();

        // Y Talento Humano tampoco puede rescatarla después.
        assertThatThrownBy(() -> workflow.aprobarSupervisor(s.getId(), rrhh, "la apruebo yo"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    // ── Segunda instancia ────────────────────────────────────────────────────

    @Test
    void talentoHumanoAprobandoDejaLaSolicitudValidada() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_SUPERVISOR);

        workflow.aprobarSupervisor(s.getId(), rrhh, "Validada");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.APROBADO);
        assertThat(s.getDecididoRrhhPor()).isSameAs(rrhh);
    }

    @Test
    void siRrhhDeniegaElPermisoNoQuedaValidado() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_SUPERVISOR);

        workflow.rechazarSupervisor(s.getId(), rrhh, "Fuera de política");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADO_RRHH);
        assertThat(s.getEstado().esFinal()).isTrue();
    }

    @Test
    void elDobleClicNoApruebaDosVeces() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_SUPERVISOR);

        workflow.aprobarSupervisor(s.getId(), rrhh, "Validada");

        // El segundo clic ya no encuentra una transición válida: el estado previo cambió
        // dentro de la misma transacción (§5.2).
        assertThatThrownBy(() -> workflow.aprobarSupervisor(s.getId(), rrhh, "Validada"))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    // ── Saldo de vacaciones ──────────────────────────────────────────────────

    @Test
    void unPermisoNoTocaElSaldoDeVacaciones() {
        Solicitud s = borradorGuardado();

        workflow.enviar(s.getId(), colaborador);

        verify(vacacionesService, never()).reservar(any());
    }

    @Test
    void lasVacacionesReservanDiasAlEnviarse() {
        Solicitud s = workflow.crearBorrador(colaborador, tipoVacaciones(), LUNES, VIERNES, null);
        when(solicitudRepository.buscarCompleta(s.getId())).thenReturn(Optional.of(s));

        workflow.enviar(s.getId(), colaborador);

        verify(vacacionesService).reservar(s);
    }

    @Test
    void alAprobarseLasVacacionesLaReservaSeVuelveConsumo() {
        Solicitud s = enBandejaDeVacaciones(EstadoSolicitud.PENDIENTE_SUPERVISOR);

        workflow.aprobarSupervisor(s.getId(), rrhh, "Validada");

        verify(vacacionesService).consumir(s, rrhh);
        verify(vacacionesService, never()).revertirReserva(any(), any(), anyString());
    }

    @Test
    void alDenegarseLasVacacionesLosDiasVuelvenAlSaldo() {
        Solicitud s = enBandejaDeVacaciones(EstadoSolicitud.PENDIENTE_COORDINADOR);

        workflow.rechazarCoordinador(s.getId(), coordinador, "Coincide con inventario");

        verify(vacacionesService).revertirReserva(any(), any(), anyString());
        verify(vacacionesService, never()).consumir(any(), any());
    }

    // ── Historial ────────────────────────────────────────────────────────────

    @Test
    void cadaTransicionDejaLineaEnElHistorialConQuienYPorQue() {
        Solicitud s = enBandeja(EstadoSolicitud.PENDIENTE_COORDINADOR);

        workflow.rechazarCoordinador(s.getId(), coordinador, "No hay cobertura");

        // Se anota también la creación del borrador, así que interesa la última línea.
        ArgumentCaptor<SolicitudHistorial> captor = ArgumentCaptor.forClass(SolicitudHistorial.class);
        verify(historialRepository, atLeastOnce()).save(captor.capture());

        SolicitudHistorial linea = captor.getValue();
        assertThat(linea.getEstadoOrigen()).isEqualTo(EstadoSolicitud.PENDIENTE_COORDINADOR);
        assertThat(linea.getEstadoDestino()).isEqualTo(EstadoSolicitud.RECHAZADO_COORD);
        assertThat(linea.getActor()).isSameAs(coordinador);
        assertThat(linea.getComentario()).isEqualTo("No hay cobertura");
    }

    // ── Ayudas ───────────────────────────────────────────────────────────────

    private Solicitud borradorGuardado() {
        Solicitud s = workflow.crearBorrador(colaborador, tipoPermiso(), LUNES, VIERNES, "Trámite");
        when(solicitudRepository.buscarCompleta(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private Solicitud enBandeja(EstadoSolicitud estado) {
        Solicitud s = borradorGuardado();
        s.setEstado(estado);
        return s;
    }

    private Solicitud enBandejaDeVacaciones(EstadoSolicitud estado) {
        Solicitud s = workflow.crearBorrador(colaborador, tipoVacaciones(), LUNES, VIERNES, null);
        when(solicitudRepository.buscarCompleta(s.getId())).thenReturn(Optional.of(s));
        s.setEstado(estado);
        return s;
    }

    private SolicitudTipo tipoPermiso() {
        SolicitudTipo tipo = new SolicitudTipo();
        tipo.setCodigo("REMUNERADO");
        tipo.setNombre("Permiso remunerado");
        tipo.setFamilia(FamiliaSolicitud.PERMISO);
        tipo.setDescuentaSaldo(false);
        return tipo;
    }

    private SolicitudTipo tipoVacaciones() {
        SolicitudTipo tipo = new SolicitudTipo();
        tipo.setCodigo(SolicitudTipo.VACACIONES);
        tipo.setNombre("Vacaciones");
        tipo.setFamilia(FamiliaSolicitud.VACACIONES);
        tipo.setDescuentaSaldo(true);
        return tipo;
    }

    private Usuario usuario(String id, String email, String rolCodigo) {
        Rol rol = new Rol();
        rol.setCodigo(rolCodigo);
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.getRoles().add(rol);
        return usuario;
    }
}
