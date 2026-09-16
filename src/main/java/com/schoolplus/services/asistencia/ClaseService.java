package com.schoolplus.services.asistencia;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Clase;
import com.schoolplus.model.ClaseHorario;
import com.schoolplus.model.ClaseInscripcion;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Rol;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.ClaseHorarioRepository;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.repository.ClaseRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alta y mantenimiento de clases: datos, horario y matrícula.
 *
 * <p>Lo administra quien tenga {@code CLASE_ADMINISTRAR} (rectoría y ADMIN). El profesor de
 * la clase no la crea ni matricula: él toma asistencia. Esa separación es la misma que ya
 * existe entre talento humano y los colaboradores en el resto del sistema.</p>
 */
@Service
public class ClaseService {

    private static final Logger log = LoggerFactory.getLogger(ClaseService.class);

    private final ClaseRepository claseRepository;
    private final ClaseHorarioRepository horarioRepository;
    private final ClaseInscripcionRepository inscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadService capacidades;

    public ClaseService(ClaseRepository claseRepository,
                        ClaseHorarioRepository horarioRepository,
                        ClaseInscripcionRepository inscripcionRepository,
                        UsuarioRepository usuarioRepository,
                        CapacidadService capacidades) {
        this.claseRepository = claseRepository;
        this.horarioRepository = horarioRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.usuarioRepository = usuarioRepository;
        this.capacidades = capacidades;
    }

    // ── Clases ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Clase> todas() {
        exigirAdministrar();
        return claseRepository.todasConProfesor();
    }

    @Transactional(readOnly = true)
    public Clase buscar(String claseId) {
        return claseRepository.buscarConProfesor(claseId)
                .orElseThrow(() -> new IllegalArgumentException("Esa clase no existe."));
    }

    @Transactional
    public Clase crear(Usuario actor, String nombre, String asignatura, String grupo, String periodo,
                       String profesorId) {
        exigirAdministrar();
        Clase clase = new Clase();
        clase.setId(UuidV7.nuevoId());
        aplicarDatos(clase, nombre, asignatura, grupo, periodo, profesorId);
        claseRepository.save(clase);
        log.info("Clase creada: {} (profesor {}) por {}", clase.etiqueta(),
                clase.getProfesor().getEmail(), actor.getEmail());
        return clase;
    }

    @Transactional
    public Clase actualizar(Usuario actor, String claseId, String nombre, String asignatura, String grupo,
                            String periodo, String profesorId) {
        exigirAdministrar();
        Clase clase = buscar(claseId);
        aplicarDatos(clase, nombre, asignatura, grupo, periodo, profesorId);
        log.info("Clase {} actualizada por {}", clase.etiqueta(), actor.getEmail());
        return claseRepository.save(clase);
    }

    /** Baja lógica: la clase deja de aparecer, pero su historial de asistencia se conserva. */
    @Transactional
    public void cambiarEstado(Usuario actor, String claseId, boolean activa) {
        exigirAdministrar();
        Clase clase = buscar(claseId);
        clase.setActiva(activa);
        claseRepository.save(clase);
        log.info("Clase {} {} por {}", clase.etiqueta(), activa ? "activada" : "desactivada", actor.getEmail());
    }

    // ── Horario ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClaseHorario> horariosDe(String claseId) {
        List<ClaseHorario> horarios = new ArrayList<>(horarioRepository.findByClaseId(claseId));
        horarios.sort((a, b) -> {
            int porDia = a.getDiaSemana().compareTo(b.getDiaSemana());
            return porDia != 0 ? porDia : a.getHoraInicio().compareTo(b.getHoraInicio());
        });
        return horarios;
    }

    @Transactional
    public ClaseHorario agregarHorario(Usuario actor, String claseId, DayOfWeek dia, LocalTime inicio,
                                       LocalTime fin, String aula) {
        exigirAdministrar();
        if (dia == null || inicio == null || fin == null) {
            throw new IllegalArgumentException("El día y las dos horas son obligatorios.");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La hora de fin tiene que ser posterior a la de inicio.");
        }
        ClaseHorario horario = new ClaseHorario();
        horario.setId(UuidV7.nuevoId());
        horario.setClase(buscar(claseId));
        horario.setDiaSemana(dia);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(fin);
        horario.setAula(Textos.vacioANulo(aula));
        return horarioRepository.save(horario);
    }

    @Transactional
    public void quitarHorario(Usuario actor, String horarioId) {
        exigirAdministrar();
        // Las sesiones que se abrieron con esta franja no se pierden: su horario_id queda
        // en NULL (ON DELETE SET NULL en V25) y conservan su hora de referencia.
        horarioRepository.deleteById(horarioId);
    }

    // ── Matrícula ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClaseInscripcion> inscritosDe(String claseId) {
        return inscripcionRepository.inscritosDe(claseId);
    }

    @Transactional(readOnly = true)
    public long contarInscritos(String claseId) {
        return inscripcionRepository.countByClaseIdAndActivaTrue(claseId);
    }

    @Transactional
    public ClaseInscripcion inscribir(Usuario actor, String claseId, String estudianteId) {
        exigirAdministrar();
        Clase clase = buscar(claseId);
        Usuario estudiante = usuarioRepository.findById(estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Esa persona no existe."));
        exigirQueSeaEstudiante(estudiante);

        // Volver a matricular a alguien retirado reactiva su fila: así conserva el
        // historial de asistencia que ya tenía en esa clase.
        ClaseInscripcion inscripcion = inscripcionRepository
                .findByClaseIdAndEstudianteId(claseId, estudianteId)
                .orElseGet(() -> {
                    ClaseInscripcion nueva = new ClaseInscripcion();
                    nueva.setId(UuidV7.nuevoId());
                    nueva.setClase(clase);
                    nueva.setEstudiante(estudiante);
                    return nueva;
                });
        inscripcion.setActiva(true);
        return inscripcionRepository.save(inscripcion);
    }

    /**
     * Matrícula por lista de correos, que es como llegan los grupos de verdad (pegados
     * desde una hoja de cálculo). Devuelve qué correos no existían para poder mostrarlos.
     */
    @Transactional
    public ResultadoMatricula inscribirPorCorreos(Usuario actor, String claseId, String correos) {
        exigirAdministrar();
        if (Textos.vacioANulo(correos) == null) {
            throw new IllegalArgumentException("Pega al menos un correo.");
        }
        List<String> sinCuenta = new ArrayList<>();
        int matriculados = 0;
        for (String linea : correos.split("[\\s,;]+")) {
            String correo = linea.trim().toLowerCase();
            if (correo.isEmpty()) {
                continue;
            }
            var estudiante = usuarioRepository.findByEmail(correo);
            if (estudiante.isEmpty() || !tieneRolEstudiante(estudiante.get())) {
                sinCuenta.add(correo);
                continue;
            }
            inscribir(actor, claseId, estudiante.get().getId());
            matriculados++;
        }
        log.info("Matrícula masiva en clase {}: {} matriculado(s), {} sin cuenta de estudiante",
                claseId, matriculados, sinCuenta.size());
        return new ResultadoMatricula(matriculados, sinCuenta);
    }

    @Transactional
    public void retirar(Usuario actor, String claseId, String estudianteId) {
        exigirAdministrar();
        ClaseInscripcion inscripcion = inscripcionRepository
                .findByClaseIdAndEstudianteId(claseId, estudianteId)
                .orElseThrow(() -> new IllegalArgumentException("Esa persona no está matriculada."));
        inscripcion.setActiva(false);
        inscripcionRepository.save(inscripcion);
    }

    // ── Listas para los formularios ──────────────────────────────────────────

    /** Quién puede ser profesor: cualquier persona activa que no sea solo estudiante. */
    @Transactional(readOnly = true)
    public List<Usuario> docentesDisponibles() {
        return usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.ACTIVO).stream()
                .filter(u -> !esSoloEstudiante(u))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Usuario> estudiantesDisponibles() {
        return usuarioRepository.buscarActivosPorRol(Rol.ESTUDIANTE, EstadoUsuario.ACTIVO);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void aplicarDatos(Clase clase, String nombre, String asignatura, String grupo, String periodo,
                              String profesorId) {
        if (Textos.vacioANulo(nombre) == null) {
            throw new IllegalArgumentException("La clase necesita un nombre.");
        }
        if (Textos.vacioANulo(asignatura) == null) {
            throw new IllegalArgumentException("La clase necesita una asignatura.");
        }
        if (Textos.vacioANulo(periodo) == null) {
            throw new IllegalArgumentException("La clase necesita un periodo (por ejemplo 2026-2).");
        }
        Usuario profesor = usuarioRepository.findById(profesorId == null ? "" : profesorId)
                .orElseThrow(() -> new IllegalArgumentException("Elige un profesor."));
        if (esSoloEstudiante(profesor)) {
            throw new IllegalArgumentException("Un estudiante no puede ser el profesor de una clase.");
        }
        clase.setNombre(nombre.trim());
        clase.setAsignatura(asignatura.trim());
        clase.setGrupo(Textos.vacioANulo(grupo));
        clase.setPeriodo(periodo.trim());
        clase.setProfesor(profesor);
    }

    private void exigirQueSeaEstudiante(Usuario usuario) {
        if (!tieneRolEstudiante(usuario)) {
            throw new IllegalArgumentException(
                    "Esa persona no tiene el rol Estudiante. Asígnaselo primero en Usuarios.");
        }
    }

    private static boolean tieneRolEstudiante(Usuario usuario) {
        return usuario.getRoles().stream().anyMatch(r -> Rol.ESTUDIANTE.equals(r.getCodigo()));
    }

    private static boolean esSoloEstudiante(Usuario usuario) {
        return tieneRolEstudiante(usuario) && usuario.getRoles().size() == 1;
    }

    private void exigirAdministrar() {
        if (!capacidades.puede(Capacidades.CLASE_ADMINISTRAR)) {
            throw new IllegalArgumentException("No tienes permiso para administrar clases.");
        }
    }

    /** Cuántos quedaron matriculados y qué correos no correspondían a un estudiante. */
    public record ResultadoMatricula(int matriculados, List<String> sinCuenta) {
    }
}
