package com.schoolplus.services.organigrama;

import com.schoolplus.model.Cargo;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.JefeCargo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CargoRepository;
import com.schoolplus.repository.JefeCargoRepository;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El organigrama que RH administra (docs/DECISIONES.md D-017).
 *
 * <p>Dos piezas, dos responsabilidades distintas:</p>
 * <ul>
 *   <li>{@code jefe_cargo} (V10): declarativo, por cargo. "Quien tenga el cargo
 *       Mensajero reporta a Juan Pérez". Lo administra RH desde una sola pantalla.</li>
 *   <li>{@code usuario.coordinador_id} (V1): el campo real por persona, que
 *       {@code SolicitudWorkflowService} y {@code CapacidadService} leen para decidir
 *       aprobaciones y ámbito AREA. Esta clase lo escribe —a partir de la sugerencia de
 *       {@code jefe_cargo} o de una excepción manual— pero nunca lo reemplaza como
 *       fuente de verdad: el resto del sistema sigue sin saber que {@code jefe_cargo}
 *       existe.</li>
 * </ul>
 */
@Service
public class OrganigramaService {

    private final JefeCargoRepository jefeCargoRepository;
    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;

    public OrganigramaService(JefeCargoRepository jefeCargoRepository,
                              CargoRepository cargoRepository,
                              UsuarioRepository usuarioRepository) {
        this.jefeCargoRepository = jefeCargoRepository;
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ── Jefe ↔ cargos a su mando ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JefeCargo> cargosDe(String jefeId) {
        return jefeCargoRepository.findByJefeIdOrderByCargo_NombreAsc(jefeId);
    }

    /** El organigrama completo (todos los cargos que ya tienen jefe asignado), para la pantalla de RH. */
    @Transactional(readOnly = true)
    public List<JefeCargo> todasLasAsignaciones() {
        return jefeCargoRepository.findAllConCargoYJefeOrderByCargo_NombreAsc();
    }

    /**
     * Fija el conjunto completo de cargos al mando de un jefe: los que no estaban se
     * agregan, los que ya no vienen en la lista se liberan. Un cargo que ya colgaba de
     * OTRO jefe se le "roba" a propósito: en el organigrama real un cargo no puede
     * reportar a dos jefes a la vez, y la pantalla de RH se piensa como "estos son
     * todos los cargos de Juan", no como altas sueltas.
     */
    @Transactional
    public void asignarCargos(String jefeId, Set<String> cargoIds) {
        Usuario jefe = usuarioRepository.findById(jefeId)
                .orElseThrow(() -> new IllegalArgumentException("El jefe " + jefeId + " no existe."));

        jefeCargoRepository.deleteByJefeIdAndCargoIdNotIn(jefeId, List.copyOf(cargoIds));

        for (String cargoId : cargoIds) {
            Cargo cargo = cargoRepository.findById(cargoId)
                    .orElseThrow(() -> new IllegalArgumentException("El cargo " + cargoId + " no existe."));

            JefeCargo asignacion = jefeCargoRepository.findById(cargoId).orElseGet(JefeCargo::new);
            asignacion.setCargo(cargo);
            asignacion.setCargoId(cargoId);
            asignacion.setJefe(jefe);
            jefeCargoRepository.save(asignacion);
        }
    }

    /** A quién sugiere como jefe el cargo actual del colaborador. Vacío si el cargo no tiene jefe asignado. */
    @Transactional(readOnly = true)
    public Optional<Usuario> jefeSugeridoPara(Usuario colaborador) {
        Cargo cargo = colaborador.getCargo();
        if (cargo == null) {
            return Optional.empty();
        }
        return jefeCargoRepository.findById(cargo.getId()).map(JefeCargo::getJefe);
    }

    /**
     * Todos los colaboradores cuyo jefe directo actual NO coincide con lo que sugiere
     * {@code jefe_cargo} para su cargo: alguien a quien RH le puso un jefe a mano, o le
     * quitó el sugerido sin poner otro. Para que la pantalla de RH muestre qué
     * excepciones manuales existen, no solo dejar cargarlas a ciegas.
     */
    @Transactional(readOnly = true)
    public List<ExcepcionManual> excepcionesManuales() {
        Map<String, JefeCargo> sugerenciaPorCargo = jefeCargoRepository
                .findAllConCargoYJefeOrderByCargo_NombreAsc().stream()
                .collect(Collectors.toMap(JefeCargo::getCargoId, jc -> jc));

        return usuarioRepository.buscarActivosConCargoYCoordinador(EstadoUsuario.ACTIVO).stream()
                .filter(u -> esExcepcion(u, sugerenciaPorCargo))
                .map(u -> new ExcepcionManual(u, sugeridoDe(u, sugerenciaPorCargo)))
                .toList();
    }

    private Usuario sugeridoDe(Usuario colaborador, Map<String, JefeCargo> sugerenciaPorCargo) {
        if (colaborador.getCargo() == null) {
            return null;
        }
        JefeCargo sugerencia = sugerenciaPorCargo.get(colaborador.getCargo().getId());
        return sugerencia == null ? null : sugerencia.getJefe();
    }

    private boolean esExcepcion(Usuario colaborador, Map<String, JefeCargo> sugerenciaPorCargo) {
        Usuario sugerido = sugeridoDe(colaborador, sugerenciaPorCargo);
        Usuario actual = colaborador.getCoordinador();
        if (sugerido == null) {
            return actual != null;
        }
        return actual == null || !actual.getId().equals(sugerido.getId());
    }

    // ── Árbol "quién es jefe de quién" ───────────────────────────────────────

    /**
     * Todo el organigrama como árbol, raíces primero (quien no tiene jefe, o cuyo jefe
     * está inactivo). Para la vista de solo lectura que consultan ADMIN, SUPERVISOR y
     * COORDINADOR (D-021 y la ampliación del mismo día).
     *
     * <p>Corta ciclos preexistentes en los datos en vez de reventar: {@code reasignarJefe}
     * impide crear uno nuevo desde la app, pero no hay garantía sobre datos que hayan
     * entrado por otra vía (migración de otro sistema, corrección manual en base).</p>
     */
    @Transactional(readOnly = true)
    public List<NodoOrganigrama> arbolOrganizacional() {
        List<Usuario> usuarios = usuarioRepository.buscarActivosConCargoYCoordinador(EstadoUsuario.ACTIVO);
        Set<String> idsCargados = usuarios.stream().map(Usuario::getId).collect(Collectors.toSet());

        Map<String, List<Usuario>> hijosPorJefe = usuarios.stream()
                .filter(u -> u.getCoordinador() != null && idsCargados.contains(u.getCoordinador().getId()))
                .collect(Collectors.groupingBy(u -> u.getCoordinador().getId()));

        List<Usuario> raices = usuarios.stream()
                .filter(u -> u.getCoordinador() == null || !idsCargados.contains(u.getCoordinador().getId()))
                .sorted(Comparator.comparing(Usuario::getNombre))
                .toList();

        return raices.stream().map(r -> construirNodo(r, hijosPorJefe, new HashSet<>())).toList();
    }

    private NodoOrganigrama construirNodo(Usuario persona, Map<String, List<Usuario>> hijosPorJefe,
                                          Set<String> ancestros) {
        Set<String> conEstaPersona = new HashSet<>(ancestros);
        conEstaPersona.add(persona.getId());

        List<NodoOrganigrama> subordinados = hijosPorJefe.getOrDefault(persona.getId(), List.of()).stream()
                .filter(hijo -> !ancestros.contains(hijo.getId()))
                .sorted(Comparator.comparing(Usuario::getNombre))
                .map(hijo -> construirNodo(hijo, hijosPorJefe, conEstaPersona))
                .toList();

        return new NodoOrganigrama(persona, subordinados);
    }

    // ── Excepción manual: coordinador_id de una persona concreta ────────────

    /**
     * Reasigna el jefe directo de un colaborador, saltándose lo que diga
     * {@code jefe_cargo} para esa persona puntual.
     *
     * <p>Valida que no se cree un ciclo: si {@code nuevoJefe} está, directa o
     * transitivamente, a las órdenes de {@code colaborador}, la cadena de aprobación
     * quedaría dando vueltas y {@code CapacidadService.esMio()} nunca terminaría de
     * resolver "mi equipo". Misma idea que D-016 (nadie se aprueba a sí mismo), llevada
     * a toda la cadena y no solo al primer eslabón.</p>
     */
    @Transactional
    public void reasignarJefe(Usuario colaborador, Usuario nuevoJefe) {
        if (nuevoJefe == null) {
            colaborador.setCoordinador(null);
            usuarioRepository.save(colaborador);
            return;
        }
        if (colaborador.getId().equals(nuevoJefe.getId())) {
            throw new CicloDeOrganigramaException("Un colaborador no puede ser su propio jefe.");
        }
        if (creaCiclo(colaborador.getId(), nuevoJefe)) {
            throw new CicloDeOrganigramaException(
                    nuevoJefe.getNombre() + " ya está, directa o indirectamente, a cargo de "
                            + colaborador.getNombre() + ". Asignarlo como jefe cerraría un ciclo.");
        }
        colaborador.setCoordinador(nuevoJefe);
        usuarioRepository.save(colaborador);
    }

    /** ¿Alguien en la cadena de jefes de {@code candidatoAJefe} es {@code colaboradorId}? */
    private boolean creaCiclo(String colaboradorId, Usuario candidatoAJefe) {
        Set<String> visitados = new HashSet<>();
        Usuario actual = candidatoAJefe;
        while (actual != null && actual.getCoordinador() != null) {
            String siguienteId = actual.getCoordinador().getId();
            if (siguienteId.equals(colaboradorId)) {
                return true;
            }
            if (!visitados.add(siguienteId)) {
                // Ciclo preexistente en datos que no viene al caso resolver aquí: no es
                // el que esta llamada está a punto de crear.
                return false;
            }
            actual = usuarioRepository.findById(siguienteId).orElse(null);
        }
        return false;
    }
}
