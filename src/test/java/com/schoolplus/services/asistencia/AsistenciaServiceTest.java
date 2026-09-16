package com.schoolplus.services.asistencia;

import com.schoolplus.model.Ambito;
import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Clase;
import com.schoolplus.model.ClaseHorario;
import com.schoolplus.model.ClaseInscripcion;
import com.schoolplus.model.EstadoAsistencia;
import com.schoolplus.model.EstadoSesionAsistencia;
import com.schoolplus.model.MetodoAsistencia;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AsistenciaRegistroRepository;
import com.schoolplus.repository.AsistenciaSesionRepository;
import com.schoolplus.repository.ClaseHorarioRepository;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.repository.ClaseRepository;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas del registro de asistencia: quién abre, quién puede registrarse, cuándo es
 * tarde y qué pasa al cerrar.
 *
 * <p>El reloj se fija con {@link Clock#fixed} porque media clase de este módulo es
 * aritmética de horas: "presente hasta las 08:10, tarde después".</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsistenciaServiceTest {

    /** 08:00 en punto, hora local del test. */
    private static final Instant INICIO_CLASE = Instant.parse("2026-09-15T13:00:00Z");
    private static final String CLASE_ID = "clase-1";
    private static final String SESION_ID = "sesion-1";
    private static final String COOKIE = "cookie-del-celular";

    @Mock
    private ClaseRepository claseRepository;
    @Mock
    private ClaseHorarioRepository horarioRepository;
    @Mock
    private ClaseInscripcionRepository inscripcionRepository;
    @Mock
    private AsistenciaSesionRepository sesionRepository;
    @Mock
    private AsistenciaRegistroRepository registroRepository;
    @Mock
    private DispositivoService dispositivos;
    @Mock
    private CapacidadService capacidades;

    private ReglasAsistencia reglas;
    private Usuario profesor;
    private Usuario otroProfesor;
    private Usuario estudiante;
    private Clase clase;

    @BeforeEach
    void preparar() {
        reglas = new ReglasAsistencia(15, 1, 120, 10, 4, false, 0, 0, 300);
        profesor = usuario("doc-1", "profe@schoolplus.example");
        otroProfesor = usuario("doc-2", "otro@schoolplus.example");
        estudiante = usuario("est-1", "ana@schoolplus.example");

        clase = new Clase();
        clase.setId(CLASE_ID);
        clase.setNombre("Matemáticas 7A");
        clase.setAsignatura("Matemáticas");
        clase.setPeriodo("2026-2");
        clase.setProfesor(profesor);
        clase.setActiva(true);

        when(claseRepository.buscarConProfesor(CLASE_ID)).thenReturn(Optional.of(clase));
        when(sesionRepository.save(any(AsistenciaSesion.class))).thenAnswer(i -> i.getArgument(0));
        when(registroRepository.save(any(AsistenciaRegistro.class))).thenAnswer(i -> i.getArgument(0));
        when(dispositivos.autorizarParaRegistro(any(), any(), any())).thenReturn(dispositivo());
    }

    // ── Abrir ────────────────────────────────────────────────────────────────

    @Test
    void elProfesorDeLaClaseAbreSuSesión() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        when(sesionRepository.findByClaseAbiertaId(CLASE_ID)).thenReturn(Optional.empty());

        AsistenciaSesion sesion = servicio.abrir(profesor, CLASE_ID, null, null);

        assertThat(sesion.getEstado()).isEqualTo(EstadoSesionAsistencia.ABIERTA);
        assertThat(sesion.getToleranciaMinutos()).isEqualTo(10);
        // El candado del UNIQUE: mientras vale, no cabe otra sesión de esta clase.
        assertThat(sesion.getClaseAbiertaId()).isEqualTo(CLASE_ID);
        assertThat(sesion.getHoraReferencia()).isEqualTo(sesion.getAbiertaEn());
    }

    @Test
    void laClaseDeOtroProfesorNoSeAbre() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);

        assertThatThrownBy(() -> servicio.abrir(otroProfesor, CLASE_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es tuya");
    }

    @Test
    void unAdminAbreLaClaseDeCualquiera() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.TODOS);
        when(sesionRepository.findByClaseAbiertaId(CLASE_ID)).thenReturn(Optional.empty());

        assertThat(servicio.abrir(otroProfesor, CLASE_ID, null, null)).isNotNull();
    }

    @Test
    void sinLaCapacidadNoSeAbreNada() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, null);

        assertThatThrownBy(() -> servicio.abrir(profesor, CLASE_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permiso");
    }

    @Test
    void volverAAbrirDevuelveLaSesiónQueYaEstaba() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        AsistenciaSesion abierta = sesionAbierta(INICIO_CLASE, 10);
        when(sesionRepository.findByClaseAbiertaId(CLASE_ID)).thenReturn(Optional.of(abierta));

        assertThat(servicio.abrir(profesor, CLASE_ID, null, null)).isSameAs(abierta);
        verify(sesionRepository, never()).save(any());
    }

    @Test
    void conFranjaDelHorarioLaTardanzaSeMideDesdeLaHoraDeLaClase() {
        // El profesor abre el QR 20 minutos tarde, pero la clase empezaba a las 08:00.
        AsistenciaService servicio = servicioEn(INICIO_CLASE.plusSeconds(20 * 60), Ambito.PROPIO);
        when(sesionRepository.findByClaseAbiertaId(CLASE_ID)).thenReturn(Optional.empty());
        ClaseHorario horario = horario(LocalTime.of(8, 0));
        when(horarioRepository.findById("hor-1")).thenReturn(Optional.of(horario));

        AsistenciaSesion sesion = servicio.abrir(profesor, CLASE_ID, "hor-1", 10);

        assertThat(sesion.getHoraReferencia().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(sesion.limiteDePuntualidad().toLocalTime()).isEqualTo(LocalTime.of(8, 10));
    }

    @Test
    void laToleranciaFueraDeRangoSeRechaza() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        when(sesionRepository.findByClaseAbiertaId(CLASE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.abrir(profesor, CLASE_ID, null, 999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 a 120");
    }

    // ── Registrarse ──────────────────────────────────────────────────────────

    @Test
    void dentroDeLaToleranciaQuedaPresente() {
        // 08:05, con 10 minutos de tolerancia.
        AsistenciaRegistro registro = registrarA(INICIO_CLASE.plusSeconds(5 * 60));

        assertThat(registro.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
        assertThat(registro.getMetodo()).isEqualTo(MetodoAsistencia.QR);
        assertThat(registro.getIp()).isEqualTo("10.0.0.9");
    }

    @Test
    void pasadaLaToleranciaQuedaTarde() {
        AsistenciaRegistro registro = registrarA(INICIO_CLASE.plusSeconds(11 * 60));

        assertThat(registro.getEstado()).isEqualTo(EstadoAsistencia.TARDE);
    }

    @Test
    void justoEnElLímiteTodavíaEsPresente() {
        AsistenciaRegistro registro = registrarA(INICIO_CLASE.plusSeconds(10 * 60));

        assertThat(registro.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    @Test
    void quienNoEstáMatriculadoNoSeRegistra() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        AsistenciaSesion sesion = sesionAbierta(INICIO_CLASE, 10);
        when(sesionRepository.buscarCompleta(SESION_ID)).thenReturn(Optional.of(sesion));
        when(inscripcionRepository.existsByClaseIdAndEstudianteIdAndActivaTrue(CLASE_ID, "est-1")).thenReturn(false);

        assertThatThrownBy(() -> servicio.registrarConToken(estudiante, tokenDe(servicio, SESION_ID), contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.NO_INSCRITO);

        // Y, sobre todo, no se le vinculó un celular a alguien que no es de la clase.
        verify(dispositivos, never()).autorizarParaRegistro(any(), any(), any());
    }

    @Test
    void conLaSesiónCerradaNoSeRegistraNadie() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        AsistenciaSesion sesion = sesionAbierta(INICIO_CLASE, 10);
        sesion.cerrar(LocalDateTime.now(relojEn(INICIO_CLASE)));
        when(sesionRepository.buscarCompleta(SESION_ID)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> servicio.registrarConToken(estudiante, tokenDe(servicio, SESION_ID), contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.SESION_CERRADA);
    }

    @Test
    void nadieSeRegistraDosVeces() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        prepararSesionConInscrito(servicio);
        when(registroRepository.findBySesionIdAndEstudianteId(SESION_ID, "est-1"))
                .thenReturn(Optional.of(new AsistenciaRegistro()));

        assertThatThrownBy(() -> servicio.registrarConToken(estudiante, tokenDe(servicio, SESION_ID), contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.YA_REGISTRADO);
    }

    @Test
    void unQrViejoNoSirve() {
        AsistenciaService servicioAlAbrir = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        String token = tokenDe(servicioAlAbrir, SESION_ID);

        // Medio minuto después: pasada la ventana y su gracia.
        AsistenciaService servicioDespues = servicioEn(INICIO_CLASE.plusSeconds(31), Ambito.PROPIO);
        prepararSesionConInscrito(servicioDespues);

        assertThatThrownBy(() -> servicioDespues.registrarConToken(estudiante, token, contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.QR_VENCIDO);
    }

    @Test
    void unTokenInventadoNoSirve() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);

        assertThatThrownBy(() -> servicio.registrarConToken(estudiante, "v1.sesion.1.firma", contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.QR_INVALIDO);
    }

    @Test
    void elMismoCelularNoRegistraADosPersonasEnLaMismaSesión() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        prepararSesionConInscrito(servicio);
        when(registroRepository.existsBySesionIdAndDispositivoId(SESION_ID, "disp-1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrarConToken(estudiante, tokenDe(servicio, SESION_ID), contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.DISPOSITIVO_YA_USADO);
    }

    // ── Código de 6 dígitos ──────────────────────────────────────────────────

    @Test
    void elCódigoEncuentraLaSesiónDeEseEstudiante() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        AsistenciaSesion sesion = prepararSesionConInscrito(servicio);
        when(sesionRepository.abiertasParaEstudiante("est-1")).thenReturn(List.of(sesion));
        String codigo = new TokenAsistenciaService("clave-de-test", reglas, relojEn(INICIO_CLASE)).codigoDe(SESION_ID);

        AsistenciaRegistro registro = servicio.registrarConCodigo(estudiante, codigo, contexto());

        assertThat(registro.getMetodo()).isEqualTo(MetodoAsistencia.CODIGO);
        assertThat(registro.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    @Test
    void unCódigoQueNoEsDeNingunaDeSusClasesFalla() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        when(sesionRepository.abiertasParaEstudiante("est-1")).thenReturn(List.of(sesionAbierta(INICIO_CLASE, 10)));

        assertThatThrownBy(() -> servicio.registrarConCodigo(estudiante, "000000", contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.CODIGO_INVALIDO);
    }

    // ── Escaneo pendiente (cámara nativa) ────────────────────────────────────

    @Test
    void unEscaneoViejoNoSeConfirma() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE.plusSeconds(200), Ambito.PROPIO);

        assertThatThrownBy(() -> servicio.registrarEscaneoPendiente(estudiante, SESION_ID,
                LocalDateTime.now(relojEn(INICIO_CLASE)), contexto()))
                .isInstanceOf(RegistroRechazadoException.class)
                .extracting(e -> ((RegistroRechazadoException) e).motivo())
                .isEqualTo(MotivoRechazo.ESCANEO_EXPIRADO);
    }

    @Test
    void unEscaneoRecienteSíSeConfirma() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE.plusSeconds(30), Ambito.PROPIO);
        prepararSesionConInscrito(servicio);

        AsistenciaRegistro registro = servicio.registrarEscaneoPendiente(estudiante, SESION_ID,
                LocalDateTime.now(relojEn(INICIO_CLASE)), contexto());

        assertThat(registro.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
    }

    // ── Cerrar ───────────────────────────────────────────────────────────────

    @Test
    void alCerrarSoloQuedanAusentesLosQueNoSeRegistraron() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE.plusSeconds(3600), Ambito.PROPIO);
        AsistenciaSesion sesion = sesionAbierta(INICIO_CLASE, 10);
        when(sesionRepository.buscarCompleta(SESION_ID)).thenReturn(Optional.of(sesion));

        Usuario presente = usuario("est-1", "ana@schoolplus.example");
        Usuario faltante = usuario("est-2", "luis@schoolplus.example");
        AsistenciaRegistro yaRegistrado = new AsistenciaRegistro();
        yaRegistrado.setEstudiante(presente);
        yaRegistrado.setEstado(EstadoAsistencia.PRESENTE);
        when(registroRepository.deSesion(SESION_ID)).thenReturn(List.of(yaRegistrado));
        when(inscripcionRepository.inscritosDe(CLASE_ID))
                .thenReturn(List.of(inscripcion(presente), inscripcion(faltante)));

        servicio.cerrar(profesor, SESION_ID);

        assertThat(sesion.getEstado()).isEqualTo(EstadoSesionAsistencia.CERRADA);
        // El candado se suelta al cerrar: la clase puede volver a abrir sesión.
        assertThat(sesion.getClaseAbiertaId()).isNull();

        ArgumentCaptor<AsistenciaRegistro> guardados = ArgumentCaptor.forClass(AsistenciaRegistro.class);
        verify(registroRepository).save(guardados.capture());
        assertThat(guardados.getValue().getEstudiante()).isSameAs(faltante);
        assertThat(guardados.getValue().getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);
        assertThat(guardados.getValue().getMetodo()).isEqualTo(MetodoAsistencia.CIERRE);
    }

    @Test
    void cerrarDosVecesNoDuplicaAusentes() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE.plusSeconds(3600), Ambito.PROPIO);
        AsistenciaSesion sesion = sesionAbierta(INICIO_CLASE, 10);
        sesion.cerrar(LocalDateTime.now(relojEn(INICIO_CLASE)));
        when(sesionRepository.buscarCompleta(SESION_ID)).thenReturn(Optional.of(sesion));

        servicio.cerrar(profesor, SESION_ID);

        verify(registroRepository, never()).save(any());
    }

    @Test
    void justificarExigeMotivo() {
        AsistenciaService servicio = servicioEn(INICIO_CLASE, Ambito.PROPIO);
        AsistenciaRegistro registro = new AsistenciaRegistro();
        registro.setId("reg-1");
        registro.setSesion(sesionAbierta(INICIO_CLASE, 10));
        registro.setEstado(EstadoAsistencia.AUSENTE);
        when(registroRepository.buscarCompleto("reg-1")).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> servicio.corregir(profesor, "reg-1", EstadoAsistencia.JUSTIFICADO, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        AsistenciaRegistro corregido = servicio.corregir(profesor, "reg-1", EstadoAsistencia.JUSTIFICADO, "Cita médica");
        assertThat(corregido.getEstado()).isEqualTo(EstadoAsistencia.JUSTIFICADO);
        assertThat(corregido.getObservacion()).isEqualTo("Cita médica");
        assertThat(corregido.getModificadoPor()).isSameAs(profesor);
    }

    // ── Ayudas ───────────────────────────────────────────────────────────────

    private AsistenciaRegistro registrarA(Instant momento) {
        AsistenciaService servicio = servicioEn(momento, Ambito.PROPIO);
        prepararSesionConInscrito(servicio);
        return servicio.registrarConToken(estudiante, tokenDe(servicio, SESION_ID), contexto());
    }

    private AsistenciaSesion prepararSesionConInscrito(AsistenciaService servicio) {
        AsistenciaSesion sesion = sesionAbierta(INICIO_CLASE, 10);
        when(sesionRepository.buscarCompleta(SESION_ID)).thenReturn(Optional.of(sesion));
        when(inscripcionRepository.existsByClaseIdAndEstudianteIdAndActivaTrue(CLASE_ID, "est-1")).thenReturn(true);
        when(registroRepository.findBySesionIdAndEstudianteId(SESION_ID, "est-1")).thenReturn(Optional.empty());
        return sesion;
    }

    /** El token que el QR mostraría en ese mismo instante. */
    private String tokenDe(AsistenciaService servicio, String sesionId) {
        return new TokenAsistenciaService("clave-de-test", reglas, relojDe(servicio)).emitir(sesionId);
    }

    private Clock relojDe(AsistenciaService servicio) {
        return relojes.get(servicio);
    }

    private final java.util.Map<AsistenciaService, Clock> relojes = new java.util.IdentityHashMap<>();

    private AsistenciaService servicioEn(Instant momento, Ambito ambito) {
        Clock reloj = relojEn(momento);
        when(capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR))
                .thenReturn(Optional.ofNullable(ambito));
        AsistenciaService servicio = new AsistenciaService(claseRepository, horarioRepository, inscripcionRepository,
                sesionRepository, registroRepository, dispositivos,
                new TokenAsistenciaService("clave-de-test", reglas, reloj),
                reglas, capacidades, reloj, "http://localhost:8080");
        relojes.put(servicio, reloj);
        return servicio;
    }

    private Clock relojEn(Instant momento) {
        return Clock.fixed(momento, ZoneOffset.UTC);
    }

    private AsistenciaSesion sesionAbierta(Instant abiertaEn, int tolerancia) {
        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setId(SESION_ID);
        sesion.setClase(clase);
        sesion.setProfesor(profesor);
        sesion.setEstado(EstadoSesionAsistencia.ABIERTA);
        sesion.setAbiertaEn(LocalDateTime.ofInstant(abiertaEn, ZoneOffset.UTC));
        sesion.setHoraReferencia(LocalDateTime.ofInstant(abiertaEn, ZoneOffset.UTC));
        sesion.setToleranciaMinutos(tolerancia);
        sesion.setClaseAbiertaId(CLASE_ID);
        return sesion;
    }

    private ClaseHorario horario(LocalTime inicio) {
        ClaseHorario horario = new ClaseHorario();
        horario.setId("hor-1");
        horario.setClase(clase);
        horario.setDiaSemana(java.time.DayOfWeek.TUESDAY);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(inicio.plusHours(1));
        return horario;
    }

    private ClaseInscripcion inscripcion(Usuario estudiante) {
        ClaseInscripcion inscripcion = new ClaseInscripcion();
        inscripcion.setId("insc-" + estudiante.getId());
        inscripcion.setClase(clase);
        inscripcion.setEstudiante(estudiante);
        inscripcion.setActiva(true);
        return inscripcion;
    }

    private AsistenciaDispositivo dispositivo() {
        AsistenciaDispositivo dispositivo = new AsistenciaDispositivo();
        dispositivo.setId("disp-1");
        dispositivo.setEstudiante(estudiante);
        dispositivo.setIdentificadorHash(DispositivoService.hashDe(COOKIE));
        return dispositivo;
    }

    private ContextoRegistro contexto() {
        return new ContextoRegistro(COOKIE, "10.0.0.9", "Android Chrome", null, null);
    }

    private Usuario usuario(String id, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre(email.split("@")[0]);
        usuario.setEmail(email);
        return usuario;
    }
}
