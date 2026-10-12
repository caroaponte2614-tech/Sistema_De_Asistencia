package com.schoolplus.services.asistencia;

import com.schoolplus.dto.DetalleSesion;
import com.schoolplus.dto.FilaRegistro;
import com.schoolplus.dto.SesionEnVivo;
import com.schoolplus.model.Ambito;
import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Clase;
import com.schoolplus.model.ClaseHorario;
import com.schoolplus.model.ClaseInscripcion;
import com.schoolplus.model.EstadoAsistencia;
import com.schoolplus.model.MetodoAsistencia;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AsistenciaRegistroRepository;
import com.schoolplus.repository.AsistenciaSesionRepository;
import com.schoolplus.repository.ClaseHorarioRepository;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.repository.ClaseRepository;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El módulo de asistencia: abrir la sesión, registrar a quien escanea y cerrar.
 *
 * <h2>Las reglas, todas en un sitio</h2>
 * <ul>
 *   <li>Abre y cierra <strong>el profesor de la clase</strong> (ámbito PROPIO) o alguien con
 *       alcance TODOS (ADMIN).</li>
 *   <li>Una sola sesión abierta por clase: lo garantiza un UNIQUE, no un {@code if}.</li>
 *   <li>Solo registra quien está <strong>matriculado y activo</strong> en esa clase.</li>
 *   <li>Un registro por estudiante y sesión; un celular no registra a dos personas.</li>
 *   <li>PRESENTE hasta la hora de referencia más la tolerancia; después, TARDE.</li>
 *   <li>Al cerrar, los matriculados sin registro quedan AUSENTE.</li>
 * </ul>
 *
 * <p>El aviso en vivo a la pantalla del profesor NO se hace aquí: igual que el resto del
 * proyecto, notificar es cosa del llamador después del commit.</p>
 */
@Service
public class AsistenciaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaService.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SESIONES_RECIENTES = 10;

    private final ClaseRepository claseRepository;
    private final ClaseHorarioRepository horarioRepository;
    private final ClaseInscripcionRepository inscripcionRepository;
    private final AsistenciaSesionRepository sesionRepository;
    private final AsistenciaRegistroRepository registroRepository;
    private final DispositivoService dispositivos;
    private final TokenAsistenciaService tokens;
    private final ReglasAsistencia reglas;
    private final CapacidadService capacidades;
    private final Clock reloj;
    private final String baseUrl;

    public AsistenciaService(ClaseRepository claseRepository,
                             ClaseHorarioRepository horarioRepository,
                             ClaseInscripcionRepository inscripcionRepository,
                             AsistenciaSesionRepository sesionRepository,
                             AsistenciaRegistroRepository registroRepository,
                             DispositivoService dispositivos,
                             TokenAsistenciaService tokens,
                             ReglasAsistencia reglas,
                             CapacidadService capacidades,
                             Clock reloj,
                             @Value("${app.base-url}") String baseUrl) {
        this.claseRepository = claseRepository;
        this.horarioRepository = horarioRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.sesionRepository = sesionRepository;
        this.registroRepository = registroRepository;
        this.dispositivos = dispositivos;
        this.tokens = tokens;
        this.reglas = reglas;
        this.capacidades = capacidades;
        this.reloj = reloj;
        this.baseUrl = baseUrl;
    }

    // ── Profesor: abrir y cerrar ─────────────────────────────────────────────

    /**
     * Abre la sesión de una clase. Si ya había una abierta, devuelve esa misma: el profesor
     * que vuelve a pulsar el botón no parte la asistencia en dos.
     *
     * @param horarioId franja del horario, o {@code null} para una clase fuera de horario
     * @param toleranciaMinutos minutos de gracia; {@code null} usa el valor configurado
     */
    @Transactional
    public AsistenciaSesion abrir(Usuario actor, String claseId, String horarioId, Integer toleranciaMinutos) {
        Clase clase = claseRepository.buscarConProfesor(claseId)
                .orElseThrow(() -> new IllegalArgumentException("Esa clase no existe."));
        exigirPuedeTomar(actor, clase);
        if (!clase.isActiva()) {
            throw new IllegalArgumentException("Esa clase está inactiva.");
        }

        Optional<AsistenciaSesion> yaAbierta = sesionRepository.findByClaseAbiertaId(claseId);
        if (yaAbierta.isPresent()) {
            return yaAbierta.get();
        }

        int tolerancia = toleranciaMinutos == null ? reglas.toleranciaPorDefectoMinutos() : toleranciaMinutos;
        if (tolerancia < 0 || tolerancia > 120) {
            throw new IllegalArgumentException("La tolerancia va de 0 a 120 minutos.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        ClaseHorario horario = horarioDe(clase, horarioId);

        AsistenciaSesion sesion = new AsistenciaSesion();
        sesion.setId(UuidV7.nuevoId());
        sesion.setClase(clase);
        sesion.setProfesor(actor);
        sesion.setHorario(horario);
        sesion.setAbiertaEn(ahora);
        // Con franja elegida, la tardanza se mide desde la hora a la que empezaba la clase
        // —aunque el profesor abra el QR diez minutos tarde—; sin franja, desde ahora.
        sesion.setHoraReferencia(horario == null ? ahora : ahora.toLocalDate().atTime(horario.getHoraInicio()));
        sesion.setToleranciaMinutos(tolerancia);
        sesion.setClaseAbiertaId(clase.getId());

        sesionRepository.save(sesion);
        log.info("Sesión de asistencia abierta en {} por {}", clase.etiqueta(), actor.getEmail());
        return sesion;
    }

    /** Cierra la sesión y deja AUSENTE a los matriculados que no se registraron. */
    @Transactional
    public AsistenciaSesion cerrar(Usuario actor, String sesionId) {
        AsistenciaSesion sesion = cargarSesion(sesionId);
        exigirPuedeTomar(actor, sesion.getClase());
        return cerrarSesion(sesion, actor);
    }

    /** El cierre que hace el job cuando una sesión quedó abierta más de la cuenta. */
    @Transactional
    public AsistenciaSesion cerrarPorSistema(String sesionId) {
        return cerrarSesion(cargarSesion(sesionId), null);
    }

    private AsistenciaSesion cerrarSesion(AsistenciaSesion sesion, Usuario actor) {
        if (!sesion.estaAbierta()) {
            return sesion;
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        sesion.cerrar(ahora);
        sesionRepository.save(sesion);

        List<ClaseInscripcion> inscritos = inscripcionRepository.inscritosDe(sesion.getClase().getId());
        Set<String> conRegistro = registroRepository.deSesion(sesion.getId()).stream()
                .map(r -> r.getEstudiante().getId())
                .collect(java.util.stream.Collectors.toSet());

        int ausentes = 0;
        for (ClaseInscripcion inscripcion : inscritos) {
            if (conRegistro.contains(inscripcion.getEstudiante().getId())) {
                continue;
            }
            AsistenciaRegistro ausente = new AsistenciaRegistro();
            ausente.setId(UuidV7.nuevoId());
            ausente.setSesion(sesion);
            ausente.setEstudiante(inscripcion.getEstudiante());
            ausente.setEstado(EstadoAsistencia.AUSENTE);
            ausente.setMetodo(MetodoAsistencia.CIERRE);
            ausente.setRegistradoEn(ahora);
            registroRepository.save(ausente);
            ausentes++;
        }
        log.info("Sesión {} cerrada por {}: {} ausente(s)", sesion.getId(),
                actor == null ? "el sistema" : actor.getEmail(), ausentes);
        return sesion;
    }

    // ── Profesor: correcciones ───────────────────────────────────────────────

    /** Cambia el estado de un registro (justificar una falta, corregir una tardanza…). */
    @Transactional
    public AsistenciaRegistro corregir(Usuario actor, String registroId, EstadoAsistencia nuevoEstado,
                                       String observacion) {
        AsistenciaRegistro registro = registroRepository.buscarCompleto(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Ese registro no existe."));
        exigirPuedeTomar(actor, registro.getSesion().getClase());
        if (nuevoEstado == EstadoAsistencia.JUSTIFICADO && Textos.vacioANulo(observacion) == null) {
            throw new IllegalArgumentException("Para justificar una falta hay que escribir el motivo.");
        }
        registro.setEstado(nuevoEstado);
        registro.setObservacion(Textos.vacioANulo(observacion));
        registro.setModificadoPor(actor);
        registro.setModificadoEn(LocalDateTime.now(reloj));
        return registroRepository.save(registro);
    }

    /**
     * Marca a mano a un estudiante: el que no tiene celular, o el que llegó cuando el
     * profesor ya había cerrado. Si ya tenía registro, lo corrige.
     */
    @Transactional
    public AsistenciaRegistro marcarAMano(Usuario actor, String sesionId, String estudianteId,
                                          EstadoAsistencia estado, String observacion) {
        AsistenciaSesion sesion = cargarSesion(sesionId);
        exigirPuedeTomar(actor, sesion.getClase());

        ClaseInscripcion inscripcion = inscripcionRepository
                .findByClaseIdAndEstudianteId(sesion.getClase().getId(), estudianteId)
                .filter(ClaseInscripcion::isActiva)
                .orElseThrow(() -> new IllegalArgumentException("Ese estudiante no está matriculado en la clase."));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        AsistenciaRegistro registro = registroRepository
                .findBySesionIdAndEstudianteId(sesionId, estudianteId)
                .orElseGet(() -> {
                    AsistenciaRegistro nuevo = new AsistenciaRegistro();
                    nuevo.setId(UuidV7.nuevoId());
                    nuevo.setSesion(sesion);
                    nuevo.setEstudiante(inscripcion.getEstudiante());
                    nuevo.setRegistradoEn(ahora);
                    return nuevo;
                });
        registro.setEstado(estado);
        registro.setMetodo(MetodoAsistencia.MANUAL);
        registro.setObservacion(Textos.vacioANulo(observacion));
        registro.setModificadoPor(actor);
        registro.setModificadoEn(ahora);
        return registroRepository.save(registro);
    }

    // ── Estudiante: registrarse ──────────────────────────────────────────────

    /** Registro por QR: el token lo acaba de leer la cámara. */
    @Transactional(noRollbackFor = RegistroRechazadoException.class)
    public AsistenciaRegistro registrarConToken(Usuario estudiante, String token, ContextoRegistro contexto) {
        TokenAsistenciaService.Verificacion verificacion = tokens.verificar(token);
        return switch (verificacion.estado()) {
            case VALIDO -> registrar(estudiante, verificacion.sesionId(), MetodoAsistencia.QR, contexto);
            case VENCIDO -> throw new RegistroRechazadoException(MotivoRechazo.QR_VENCIDO);
            case INVALIDO -> throw new RegistroRechazadoException(MotivoRechazo.QR_INVALIDO);
        };
    }

    /**
     * Registro con el código de 6 dígitos, para quien no puede o no quiere usar la cámara.
     *
     * <p>El código no dice a qué sesión pertenece: se prueba contra las sesiones abiertas de
     * las clases de ese estudiante, que son una o dos. Así el estudiante no tiene que
     * elegir clase en una lista.</p>
     */
    @Transactional(noRollbackFor = RegistroRechazadoException.class)
    public AsistenciaRegistro registrarConCodigo(Usuario estudiante, String codigo, ContextoRegistro contexto) {
        String limpio = codigo == null ? "" : codigo.replaceAll("\\D", "");
        if (limpio.length() != 6) {
            throw new RegistroRechazadoException(MotivoRechazo.CODIGO_INVALIDO);
        }
        AsistenciaSesion sesion = sesionRepository.abiertasParaEstudiante(estudiante.getId()).stream()
                .filter(s -> tokens.codigoCoincide(s.getId(), limpio))
                .findFirst()
                .orElseThrow(() -> new RegistroRechazadoException(MotivoRechazo.CODIGO_INVALIDO));
        return registrar(estudiante, sesion.getId(), MetodoAsistencia.CODIGO, contexto);
    }

    /**
     * Registro a partir de un escaneo ya validado que quedó guardado en la sesión HTTP.
     *
     * <p>Es el camino de la cámara nativa del teléfono: el QR abre un enlace, se comprueba
     * el token ahí mismo y, si hace falta iniciar sesión o conceder la ubicación, el
     * escaneo espera unos segundos ({@link ReglasAsistencia#segundosParaConfirmar()}).</p>
     */
    @Transactional(noRollbackFor = RegistroRechazadoException.class)
    public AsistenciaRegistro registrarEscaneoPendiente(Usuario estudiante, String sesionId,
                                                        LocalDateTime escaneadoEn, ContextoRegistro contexto) {
        if (sesionId == null || escaneadoEn == null) {
            throw new RegistroRechazadoException(MotivoRechazo.QR_INVALIDO);
        }
        long segundos = Duration.between(escaneadoEn, LocalDateTime.now(reloj)).getSeconds();
        if (segundos > reglas.segundosParaConfirmar() || segundos < 0) {
            throw new RegistroRechazadoException(MotivoRechazo.ESCANEO_EXPIRADO);
        }
        return registrar(estudiante, sesionId, MetodoAsistencia.QR, contexto);
    }

    /**
     * El corazón del módulo: las comprobaciones en el orden que importa.
     *
     * <p>Primero lo que depende de la sesión y de la matrícula, y solo después el celular:
     * así un intento de alguien que ni siquiera está en la clase no le vincula un teléfono
     * a nadie.</p>
     *
     * <p>Cada rechazo se lanza antes de escribir nada, salvo el de celular nuevo, que deja
     * guardada la solicitud de cambio y el aviso a los profesores. Por eso los métodos que
     * llegan aquí no deshacen la transacción ante {@link RegistroRechazadoException}: una
     * comprobación nueva tiene que respetar ese orden.</p>
     */
    private AsistenciaRegistro registrar(Usuario estudiante, String sesionId, MetodoAsistencia metodo,
                                         ContextoRegistro contexto) {
        AsistenciaSesion sesion = sesionRepository.buscarCompleta(sesionId)
                .orElseThrow(() -> new RegistroRechazadoException(MotivoRechazo.QR_INVALIDO));

        if (!sesion.estaAbierta()) {
            throw new RegistroRechazadoException(MotivoRechazo.SESION_CERRADA);
        }
        if (!inscripcionRepository.existsByClaseIdAndEstudianteIdAndActivaTrue(
                sesion.getClase().getId(), estudiante.getId())) {
            throw new RegistroRechazadoException(MotivoRechazo.NO_INSCRITO);
        }
        if (registroRepository.findBySesionIdAndEstudianteId(sesionId, estudiante.getId()).isPresent()) {
            throw new RegistroRechazadoException(MotivoRechazo.YA_REGISTRADO);
        }
        if (reglas.geoHabilitada()) {
            if (contexto.latitud() == null || contexto.longitud() == null) {
                throw new RegistroRechazadoException(MotivoRechazo.UBICACION_REQUERIDA);
            }
            if (!reglas.dentroDelRadio(contexto.latitud(), contexto.longitud())) {
                throw new RegistroRechazadoException(MotivoRechazo.FUERA_DEL_COLEGIO);
            }
        }

        AsistenciaDispositivo dispositivo = dispositivos.autorizarParaRegistro(
                estudiante, contexto.identificadorDispositivo(), contexto.userAgent());
        if (registroRepository.existsBySesionIdAndDispositivoId(sesionId, dispositivo.getId())) {
            throw new RegistroRechazadoException(MotivoRechazo.DISPOSITIVO_YA_USADO);
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        AsistenciaRegistro registro = new AsistenciaRegistro();
        registro.setId(UuidV7.nuevoId());
        registro.setSesion(sesion);
        registro.setEstudiante(estudiante);
        registro.setEstado(sesion.estadoSegunLaHora(ahora));
        registro.setMetodo(metodo);
        registro.setRegistradoEn(ahora);
        registro.setDispositivo(dispositivo);
        registro.setIp(Textos.recortar(contexto.ip(), 45));
        registro.setUserAgent(Textos.recortar(contexto.userAgent(), 255));
        registro.setLatitud(contexto.latitud());
        registro.setLongitud(contexto.longitud());

        registroRepository.save(registro);
        log.info("Asistencia {} de {} en {} por {}", registro.getEstado(), estudiante.getEmail(),
                sesion.getClase().etiqueta(), metodo);
        return registro;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    /** Las clases sobre las que el actor puede tomar asistencia. */
    @Transactional(readOnly = true)
    public List<Clase> misClases(Usuario actor) {
        Ambito ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)
                .orElseThrow(() -> new IllegalArgumentException("No tienes permiso para tomar asistencia."));
        return ambito == Ambito.TODOS
                ? claseRepository.activasConProfesor()
                : claseRepository.deProfesor(actor.getId());
    }

    @Transactional(readOnly = true)
    public Optional<AsistenciaSesion> sesionAbiertaDe(String claseId) {
        return sesionRepository.findByClaseAbiertaId(claseId);
    }

    @Transactional(readOnly = true)
    public List<ClaseHorario> horariosDe(String claseId) {
        List<ClaseHorario> horarios = new ArrayList<>(horarioRepository.findByClaseId(claseId));
        // El día se guarda como texto (MONDAY, TUESDAY…): ordenar en SQL pondría el viernes
        // antes que el lunes, así que el orden cronológico se hace aquí.
        horarios.sort((a, b) -> {
            int porDia = a.getDiaSemana().compareTo(b.getDiaSemana());
            return porDia != 0 ? porDia : a.getHoraInicio().compareTo(b.getHoraInicio());
        });
        return horarios;
    }

    @Transactional(readOnly = true)
    public long contarInscritos(String claseId) {
        return inscripcionRepository.countByClaseIdAndActivaTrue(claseId);
    }

    @Transactional(readOnly = true)
    public List<AsistenciaSesion> sesionesRecientesDe(String claseId) {
        return sesionRepository.ultimasDeClase(claseId, PageRequest.of(0, SESIONES_RECIENTES));
    }

    /** Todo lo que la pantalla de proyección necesita para pintarse. */
    @Transactional(readOnly = true)
    public SesionEnVivo enVivo(Usuario actor, String sesionId) {
        AsistenciaSesion sesion = cargarSesion(sesionId);
        exigirPuedeTomar(actor, sesion.getClase());

        List<AsistenciaRegistro> registros = registroRepository.deSesion(sesionId);
        long presentes = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.PRESENTE).count();
        long tardes = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.TARDE).count();
        long inscritos = inscripcionRepository.countByClaseIdAndActivaTrue(sesion.getClase().getId());

        boolean abierta = sesion.estaAbierta();
        String enlace = abierta ? enlaceDeRegistro(tokens.emitir(sesionId)) : null;

        return new SesionEnVivo(
                sesion.getId(),
                sesion.getClase().etiqueta(),
                abierta,
                enlace,
                abierta ? QrSvg.dataUri(enlace) : null,
                abierta ? tokens.codigoDe(sesionId) : null,
                abierta ? tokens.segundosParaRotar() : 0,
                reglas.rotacionSegundos(),
                presentes,
                tardes,
                registros.size(),
                inscritos,
                registros.stream().map(AsistenciaService::aFila).toList());
    }

    /** El detalle de una sesión: lo que se ve al cerrarla y desde donde se corrige. */
    @Transactional(readOnly = true)
    public DetalleSesion detalle(Usuario actor, String sesionId) {
        AsistenciaSesion sesion = cargarSesion(sesionId);
        exigirPuedeTomar(actor, sesion.getClase());

        List<AsistenciaRegistro> registros = registroRepository.deSesion(sesionId);
        Set<String> conRegistro = registros.stream()
                .map(r -> r.getEstudiante().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<Usuario> sinRegistro = inscripcionRepository.inscritosDe(sesion.getClase().getId()).stream()
                .map(ClaseInscripcion::getEstudiante)
                .filter(e -> !conRegistro.contains(e.getId()))
                .toList();

        return new DetalleSesion(sesion, registros, sinRegistro);
    }

    /** Las clases del estudiante, para su pantalla de registro. */
    @Transactional(readOnly = true)
    public List<ClaseInscripcion> clasesDelEstudiante(Usuario estudiante) {
        return inscripcionRepository.clasesDe(estudiante.getId());
    }

    @Transactional(readOnly = true)
    public List<AsistenciaRegistro> historialDe(Usuario estudiante) {
        return registroRepository.historialDe(estudiante.getId());
    }

    /** ¿Ese actor puede ver la pantalla en vivo de esa sesión? La usa el WebSocket. */
    @Transactional(readOnly = true)
    public boolean puedeSeguirEnVivo(Usuario actor, String sesionId) {
        return sesionRepository.buscarCompleta(sesionId)
                .map(sesion -> {
                    Optional<Ambito> ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR);
                    return ambito.isPresent()
                            && (ambito.get() == Ambito.TODOS
                                || sesion.getClase().getProfesor().getId().equals(actor.getId()));
                })
                .orElse(false);
    }

    public String enlaceDeRegistro(String token) {
        return baseUrl + "/asistencia/r?t=" + token;
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private AsistenciaSesion cargarSesion(String sesionId) {
        return sesionRepository.buscarCompleta(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Esa sesión no existe."));
    }

    private ClaseHorario horarioDe(Clase clase, String horarioId) {
        if (Textos.vacioANulo(horarioId) == null) {
            return null;
        }
        ClaseHorario horario = horarioRepository.findById(horarioId)
                .orElseThrow(() -> new IllegalArgumentException("Ese horario no existe."));
        if (!horario.getClase().getId().equals(clase.getId())) {
            throw new IllegalArgumentException("Ese horario es de otra clase.");
        }
        return horario;
    }

    /**
     * Abrir, cerrar y corregir es del profesor de la clase; con alcance TODOS, de
     * cualquiera. El ámbito sale de la matriz de permisos, no de un {@code if} por rol.
     */
    private void exigirPuedeTomar(Usuario actor, Clase clase) {
        Ambito ambito = capacidades.ambitoDe(Capacidades.ASISTENCIA_TOMAR)
                .orElseThrow(() -> new IllegalArgumentException("No tienes permiso para tomar asistencia."));
        if (ambito == Ambito.TODOS) {
            return;
        }
        if (!clase.getProfesor().getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Esa clase no es tuya.");
        }
    }

    private static FilaRegistro aFila(AsistenciaRegistro registro) {
        return new FilaRegistro(
                registro.getId(),
                registro.getEstudiante().getId(),
                registro.getEstudiante().getNombre(),
                registro.getRegistradoEn().format(HORA),
                registro.getEstado().name(),
                registro.getEstado().etiqueta(),
                registro.getMetodo().etiqueta(),
                registro.fueCorregido(),
                registro.getObservacion());
    }
}
