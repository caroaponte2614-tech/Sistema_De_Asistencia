package com.schoolplus.services.tablero;

import com.schoolplus.model.Area;
import com.schoolplus.model.PrioridadTarea;
import com.schoolplus.model.Tablero;
import com.schoolplus.model.TableroColumna;
import com.schoolplus.model.TableroTarjeta;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AreaRepository;
import com.schoolplus.repository.TableroRepository;
import com.schoolplus.repository.TableroTarjetaRepository;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tablero de tareas, uno por persona (§4.3).
 *
 * <p>Origen: un proyecto de referencia (services/tablero/TableroService.java). Se conservan sus
 * cuatro columnas, sus dos prioridades y la etiqueta libre de área.</p>
 *
 * <h2>Concurrencia</h2>
 * <p>Todas las tarjetas llevan {@code @Version}. El caso real no es exótico: la misma
 * persona con el tablero abierto en el portátil y en el móvil, o dos pestañas. Sin bloqueo
 * optimista, mover una tarjeta en una pestaña y luego editar el título en la otra
 * <em>deshace</em> el movimiento sin que nadie se entere, porque la segunda escribe un
 * objeto que leyó antes del cambio.</p>
 *
 * <p>Con {@code @Version}, esa segunda escritura lanza
 * {@code OptimisticLockingFailureException}, que
 * {@code ManejadorDeConcurrencia} traduce a <strong>409 Conflict</strong> y el cliente
 * recarga. El §5.2 lo pide con esas palabras: "409 Conflict con mensaje entendible en el
 * front, no un 500".</p>
 *
 * <p>Reordenar una columna sí reescribe varias tarjetas en la misma transacción: o entran
 * todas o no entra ninguna, para que no quede media columna renumerada.</p>
 */
@Service
public class TableroService {

    private static final Logger log = LoggerFactory.getLogger(TableroService.class);

    /** Las cuatro columnas heredadas de ColumnaTablero, con sus etiquetas. */
    private static final String[][] COLUMNAS_INICIALES = {
            {TableroColumna.PENDIENTE, "Pendiente"},
            {TableroColumna.CURSO, "En curso"},
            {TableroColumna.ESPERA, "Esperando respuesta"},
            {TableroColumna.HECHO, "Hecho"}
    };

    private final TableroRepository tableroRepository;
    private final TableroTarjetaRepository tarjetaRepository;
    private final AreaRepository areaRepository;

    public TableroService(TableroRepository tableroRepository,
                          TableroTarjetaRepository tarjetaRepository,
                          AreaRepository areaRepository) {
        this.tableroRepository = tableroRepository;
        this.tarjetaRepository = tarjetaRepository;
        this.areaRepository = areaRepository;
    }

    /**
     * El tablero de esa persona; lo crea con sus cuatro columnas la primera vez.
     *
     * <p>Crear al vuelo, y no en un job de alta de usuario, significa que quien se registró
     * antes de que existiera el módulo también tiene tablero al entrar.</p>
     */
    @Transactional
    public Tablero tableroDe(Usuario usuario) {
        return tableroRepository.buscarConColumnas(usuario.getId())
                .orElseGet(() -> crearTablero(usuario));
    }

    @Transactional(readOnly = true)
    public List<TableroTarjeta> tarjetasDe(String tableroId) {
        return tarjetaRepository.delTablero(tableroId);
    }

    @Transactional
    public TableroTarjeta crearTarjeta(Usuario actor, String columnaId, String titulo,
                                       String etiqueta, String nota, PrioridadTarea prioridad,
                                       LocalDateTime fechaLimite) {
        String limpio = Textos.vacioANulo(titulo);
        if (limpio == null) {
            throw new IllegalArgumentException("El título de la tarea es obligatorio.");
        }

        TableroColumna columna = columnaPropia(actor, columnaId);

        TableroTarjeta tarjeta = new TableroTarjeta();
        tarjeta.setId(UuidV7.nuevoId());
        tarjeta.setColumna(columna);
        tarjeta.setTitulo(limpio);
        tarjeta.setEtiqueta(Textos.vacioANulo(etiqueta));
        tarjeta.setNota(Textos.vacioANulo(nota));
        tarjeta.setPrioridad(prioridad == null ? PrioridadTarea.NORMAL : prioridad);
        tarjeta.setFechaLimite(fechaLimite);
        tarjeta.setOrden(tarjetaRepository.siguienteOrden(columna.getId()));
        tarjeta.setCreadoPor(actor);

        return tarjetaRepository.save(tarjeta);
    }

    @Transactional
    public TableroTarjeta actualizarTarjeta(Usuario actor, String tarjetaId, long versionVista,
                                            String titulo, String etiqueta, String nota,
                                            PrioridadTarea prioridad, LocalDateTime fechaLimite) {
        TableroTarjeta tarjeta = tarjetaPropia(actor, tarjetaId);
        exigirVersion(tarjeta, versionVista);

        String limpio = Textos.vacioANulo(titulo);
        if (limpio == null) {
            throw new IllegalArgumentException("El título de la tarea es obligatorio.");
        }
        tarjeta.setTitulo(limpio);
        tarjeta.setEtiqueta(Textos.vacioANulo(etiqueta));
        tarjeta.setNota(Textos.vacioANulo(nota));
        if (prioridad != null) {
            tarjeta.setPrioridad(prioridad);
        }
        // Una fecha límite nueva merece su propio recordatorio, aunque la anterior ya
        // hubiera avisado.
        if (!java.util.Objects.equals(tarjeta.getFechaLimite(), fechaLimite)) {
            tarjeta.setRecordatorioEnviado(false);
        }
        tarjeta.setFechaLimite(fechaLimite);
        return tarjetaRepository.save(tarjeta);
    }

    /**
     * Mueve una tarjeta a una columna y una posición, y renumera lo que haga falta.
     *
     * <p>Renumerar entero en vez de intercalar decimales: una columna tiene decenas de
     * tarjetas, no miles, y así los órdenes siempre son 0,1,2… sin huecos que arreglar
     * después.</p>
     */
    @Transactional
    public TableroTarjeta moverTarjeta(Usuario actor, String tarjetaId, long versionVista,
                                       String columnaDestinoId, int posicion) {
        TableroTarjeta tarjeta = tarjetaPropia(actor, tarjetaId);
        exigirVersion(tarjeta, versionVista);

        TableroColumna origen = tarjeta.getColumna();
        TableroColumna destino = columnaPropia(actor, columnaDestinoId);

        tarjeta.setColumna(destino);
        tarjetaRepository.save(tarjeta);

        // Reinsertar en la posición pedida dentro del destino.
        List<TableroTarjeta> enDestino = new java.util.ArrayList<>(
                tarjetaRepository.delTablero(destino.getTablero().getId()).stream()
                        .filter(t -> t.getColumna().getId().equals(destino.getId()))
                        .filter(t -> !t.getId().equals(tarjeta.getId()))
                        .toList());

        int indice = Math.max(0, Math.min(posicion, enDestino.size()));
        enDestino.add(indice, tarjeta);
        renumerar(enDestino);

        if (!origen.getId().equals(destino.getId())) {
            renumerar(tarjetaRepository.delTablero(origen.getTablero().getId()).stream()
                    .filter(t -> t.getColumna().getId().equals(origen.getId()))
                    .filter(t -> !t.getId().equals(tarjeta.getId()))
                    .toList());
        }

        log.debug("Tarjeta {} movida a {} posición {}", tarjetaId, destino.getCodigo(), indice);
        return tarjeta;
    }

    @Transactional
    public void eliminarTarjeta(Usuario actor, String tarjetaId) {
        TableroTarjeta tarjeta = tarjetaPropia(actor, tarjetaId);
        TableroColumna columna = tarjeta.getColumna();
        tarjetaRepository.delete(tarjeta);
        tarjetaRepository.flush();

        renumerar(tarjetaRepository.delTablero(columna.getTablero().getId()).stream()
                .filter(t -> t.getColumna().getId().equals(columna.getId()))
                .toList());
    }

    // ── Tablero de área ──────────────────────────────────────────────────────
    //
    // Uno por área (D-030), compartido por su equipo. Lo gestiona su líder
    // (area.coordinador_id, ya modelado desde la V1) y, por encima de cualquier área, el
    // ADMIN. El resto del área solo lo ve: es un tablero de equipo, no uno par-a-par.

    /** Las áreas donde esta persona pinta algo: la suya (si tiene) y las que lidera. */
    @Transactional(readOnly = true)
    public List<Area> areasRelevantesPara(Usuario actor) {
        List<Area> areas = new java.util.ArrayList<>();
        if (actor.getArea() != null) {
            areas.add(actor.getArea());
        }
        areaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .filter(a -> esLiderDe(actor, a))
                .filter(a -> areas.stream().noneMatch(existente -> existente.getId().equals(a.getId())))
                .forEach(areas::add);
        return areas;
    }

    /** Todas las áreas activas: lo que el ADMIN recorre en su vista de "todas las áreas". */
    @Transactional(readOnly = true)
    public List<Area> todasLasAreasActivas() {
        return areaRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public boolean puedeEditarAreaBoard(Usuario actor, String areaId) {
        return areaRepository.findById(areaId).map(a -> puedeEditar(actor, a)).orElse(false);
    }

    @Transactional
    public Tablero tableroDeAreaValidado(Usuario actor, String areaId) {
        return tableroDeArea(areaValidaParaVer(actor, areaId));
    }

    @Transactional
    public TableroTarjeta crearTarjetaDeArea(Usuario actor, String areaId, String columnaId,
                                             String titulo, String etiqueta, String nota,
                                             PrioridadTarea prioridad, LocalDateTime fechaLimite) {
        Area area = areaValidaParaEditar(actor, areaId);

        String limpio = Textos.vacioANulo(titulo);
        if (limpio == null) {
            throw new IllegalArgumentException("El título de la tarea es obligatorio.");
        }

        TableroColumna columna = columnaEn(tableroDeArea(area), columnaId);

        TableroTarjeta tarjeta = new TableroTarjeta();
        tarjeta.setId(UuidV7.nuevoId());
        tarjeta.setColumna(columna);
        tarjeta.setTitulo(limpio);
        tarjeta.setEtiqueta(Textos.vacioANulo(etiqueta));
        tarjeta.setNota(Textos.vacioANulo(nota));
        tarjeta.setPrioridad(prioridad == null ? PrioridadTarea.NORMAL : prioridad);
        tarjeta.setFechaLimite(fechaLimite);
        tarjeta.setOrden(tarjetaRepository.siguienteOrden(columna.getId()));
        tarjeta.setCreadoPor(actor);

        return tarjetaRepository.save(tarjeta);
    }

    @Transactional
    public TableroTarjeta actualizarTarjetaDeArea(Usuario actor, String areaId, String tarjetaId,
                                                  long versionVista, String titulo, String etiqueta,
                                                  String nota, PrioridadTarea prioridad,
                                                  LocalDateTime fechaLimite) {
        TableroTarjeta tarjeta = tarjetaDeArea(actor, areaId, tarjetaId);
        exigirVersion(tarjeta, versionVista);

        String limpio = Textos.vacioANulo(titulo);
        if (limpio == null) {
            throw new IllegalArgumentException("El título de la tarea es obligatorio.");
        }
        tarjeta.setTitulo(limpio);
        tarjeta.setEtiqueta(Textos.vacioANulo(etiqueta));
        tarjeta.setNota(Textos.vacioANulo(nota));
        if (prioridad != null) {
            tarjeta.setPrioridad(prioridad);
        }
        if (!java.util.Objects.equals(tarjeta.getFechaLimite(), fechaLimite)) {
            tarjeta.setRecordatorioEnviado(false);
        }
        tarjeta.setFechaLimite(fechaLimite);
        return tarjetaRepository.save(tarjeta);
    }

    @Transactional
    public TableroTarjeta moverTarjetaDeArea(Usuario actor, String areaId, String tarjetaId,
                                             long versionVista, String columnaDestinoId, int posicion) {
        Area area = areaValidaParaEditar(actor, areaId);
        TableroTarjeta tarjeta = tarjetaDeArea(actor, areaId, tarjetaId);
        exigirVersion(tarjeta, versionVista);

        TableroColumna origen = tarjeta.getColumna();
        TableroColumna destino = columnaEn(tableroDeArea(area), columnaDestinoId);

        tarjeta.setColumna(destino);
        tarjetaRepository.save(tarjeta);

        List<TableroTarjeta> enDestino = new java.util.ArrayList<>(
                tarjetaRepository.delTablero(destino.getTablero().getId()).stream()
                        .filter(t -> t.getColumna().getId().equals(destino.getId()))
                        .filter(t -> !t.getId().equals(tarjeta.getId()))
                        .toList());

        int indice = Math.max(0, Math.min(posicion, enDestino.size()));
        enDestino.add(indice, tarjeta);
        renumerar(enDestino);

        if (!origen.getId().equals(destino.getId())) {
            renumerar(tarjetaRepository.delTablero(origen.getTablero().getId()).stream()
                    .filter(t -> t.getColumna().getId().equals(origen.getId()))
                    .filter(t -> !t.getId().equals(tarjeta.getId()))
                    .toList());
        }

        return tarjeta;
    }

    @Transactional
    public void eliminarTarjetaDeArea(Usuario actor, String areaId, String tarjetaId) {
        TableroTarjeta tarjeta = tarjetaDeArea(actor, areaId, tarjetaId);
        TableroColumna columna = tarjeta.getColumna();
        tarjetaRepository.delete(tarjeta);
        tarjetaRepository.flush();

        renumerar(tarjetaRepository.delTablero(columna.getTablero().getId()).stream()
                .filter(t -> t.getColumna().getId().equals(columna.getId()))
                .toList());
    }

    private Tablero tableroDeArea(Area area) {
        return tableroRepository.buscarPorAreaConColumnas(area.getId())
                .orElseGet(() -> crearTableroDeArea(area));
    }

    private Area areaValidaParaVer(Usuario actor, String areaId) {
        Area area = areaActiva(areaId);
        if (!puedeVer(actor, area)) {
            throw new IllegalArgumentException("No puedes ver el tablero de esa área.");
        }
        return area;
    }

    private Area areaValidaParaEditar(Usuario actor, String areaId) {
        Area area = areaActiva(areaId);
        if (!puedeEditar(actor, area)) {
            throw new IllegalArgumentException("Solo el líder de esa área, o un administrador, puede tocar su tablero.");
        }
        return area;
    }

    private Area areaActiva(String areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa área no existe."));
        if (!area.isActivo()) {
            throw new IllegalArgumentException("Esa área está inactiva.");
        }
        return area;
    }

    private boolean puedeVer(Usuario actor, Area area) {
        return actor.esAdmin()
                || esLiderDe(actor, area)
                || (actor.getArea() != null && actor.getArea().getId().equals(area.getId()));
    }

    private boolean puedeEditar(Usuario actor, Area area) {
        return actor.esAdmin() || esLiderDe(actor, area);
    }

    private boolean esLiderDe(Usuario actor, Area area) {
        return area.getCoordinadorId() != null && area.getCoordinadorId().equals(actor.getId());
    }

    private TableroColumna columnaEn(Tablero tablero, String columnaId) {
        return tablero.getColumnas().stream()
                .filter(c -> c.getId().equals(columnaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esa columna no es de ese tablero."));
    }

    private TableroTarjeta tarjetaDeArea(Usuario actor, String areaId, String tarjetaId) {
        Area area = areaValidaParaEditar(actor, areaId);
        TableroTarjeta tarjeta = tarjetaRepository.buscarConTablero(tarjetaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa tarea no existe."));
        Tablero tableroDeLaTarjeta = tarjeta.getColumna().getTablero();
        if (tableroDeLaTarjeta.getArea() == null || !tableroDeLaTarjeta.getArea().getId().equals(area.getId())) {
            throw new IllegalArgumentException("Esa tarea no es del tablero de esa área.");
        }
        return tarjeta;
    }

    private Tablero crearTableroDeArea(Area area) {
        Tablero tablero = new Tablero();
        tablero.setId(UuidV7.nuevoId());
        tablero.setArea(area);
        tablero.setNombre("Tablero de " + area.getNombre());
        agregarColumnasIniciales(tablero);

        log.info("Tablero de área creado para {}", area.getNombre());
        return tableroRepository.save(tablero);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void renumerar(List<TableroTarjeta> tarjetas) {
        for (int i = 0; i < tarjetas.size(); i++) {
            TableroTarjeta t = tarjetas.get(i);
            if (t.getOrden() != i) {
                t.setOrden(i);
                tarjetaRepository.save(t);
            }
        }
    }

    /**
     * Comprueba que quien edita vio la versión que está guardando.
     *
     * <p>Esto se adelanta al chequeo de Hibernate a propósito: permite fallar con un
     * mensaje que la persona entiende antes de tocar la base, en vez de esperar al
     * {@code UPDATE ... WHERE version = ?} que no afecta ninguna fila.</p>
     */
    private void exigirVersion(TableroTarjeta tarjeta, long versionVista) {
        if (versionVista >= 0 && tarjeta.getVersion() != versionVista) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    TableroTarjeta.class, tarjeta.getId());
        }
    }

    /** Nadie toca el tablero de otro. El §4.3 dice "cada usuario tiene el suyo". */
    private TableroTarjeta tarjetaPropia(Usuario actor, String tarjetaId) {
        TableroTarjeta tarjeta = tarjetaRepository.buscarConTablero(tarjetaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa tarea no existe."));
        if (!tarjeta.getColumna().getTablero().getUsuario().getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Esa tarea no es de tu tablero.");
        }
        return tarjeta;
    }

    private TableroColumna columnaPropia(Usuario actor, String columnaId) {
        return columnaEn(tableroDe(actor), columnaId);
    }

    private Tablero crearTablero(Usuario usuario) {
        Tablero tablero = new Tablero();
        tablero.setId(UuidV7.nuevoId());
        tablero.setUsuario(usuario);
        tablero.setNombre("Mi tablero");
        agregarColumnasIniciales(tablero);

        log.info("Tablero creado para {}", usuario.getEmail());
        return tableroRepository.save(tablero);
    }

    private void agregarColumnasIniciales(Tablero tablero) {
        for (int i = 0; i < COLUMNAS_INICIALES.length; i++) {
            TableroColumna columna = new TableroColumna();
            columna.setId(UuidV7.nuevoId());
            columna.setTablero(tablero);
            columna.setCodigo(COLUMNAS_INICIALES[i][0]);
            columna.setNombre(COLUMNAS_INICIALES[i][1]);
            columna.setOrden(i);
            tablero.getColumnas().add(columna);
        }
    }

}
