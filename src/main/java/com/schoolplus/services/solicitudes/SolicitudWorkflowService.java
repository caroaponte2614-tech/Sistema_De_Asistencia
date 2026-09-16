package com.schoolplus.services.solicitudes;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.EstadoSolicitud;
import com.schoolplus.model.Solicitud;
import com.schoolplus.model.SolicitudHistorial;
import com.schoolplus.model.SolicitudTipo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.SolicitudHistorialRepository;
import com.schoolplus.repository.SolicitudRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.documentos.PoliticaArchivos;
import com.schoolplus.services.documentos.PoliticaArchivos.ArchivoValidado;
import com.schoolplus.services.documentos.TipoArchivo;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.services.vacaciones.VacacionesService;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * El motor de aprobación de dos instancias (§4.4 y §4.5).
 *
 * <p>Es el único sitio donde una solicitud cambia de estado. Todo lo que pasa aquí pasa
 * dentro de una transacción y deja línea en el historial.</p>
 *
 * <h2>Las reglas del §4.4, una por una</h2>
 * <ul>
 *   <li>El coordinador que aprueba es <strong>el asignado a esa solicitud</strong>, no
 *       cualquier coordinador.</li>
 *   <li>Si el coordinador deniega, <strong>el flujo termina ahí</strong>: nunca llega a
 *       RRHH. Lo garantiza {@link EstadoSolicitud}, donde RECHAZADO_COORD no tiene
 *       transiciones de salida.</li>
 *   <li>Si RRHH deniega, el permiso <strong>no queda validado</strong>. También final.</li>
 *   <li><strong>Motivo obligatorio</strong> en toda denegación.</li>
 *   <li>Un solicitante <strong>no puede ser su propio aprobador</strong>.</li>
 *   <li>Toda transición se registra: quién, cuándo, de qué estado a cuál, comentario.</li>
 * </ul>
 *
 * <h2>Idempotencia (§5.2)</h2>
 * El doble clic del coordinador no aprueba dos veces. Se resuelve comprobando el estado
 * previo <em>dentro</em> de la transacción: si la solicitud ya no está pendiente de esa
 * instancia, la segunda llamada no encuentra una transición válida y se rechaza. El
 * bloqueo optimista de {@code @Version} cubre el caso de dos aprobadores distintos a la
 * vez, que sí debe fallar con conflicto.
 *
 * <h2>Lo que NO hace</h2>
 * No envía correo ni notifica por WebSocket dentro de la transacción. El §5.2 prohíbe
 * transacciones abiertas durante llamadas de red, así que avisar es cosa del llamador,
 * después del commit.
 */
@Service
public class SolicitudWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudWorkflowService.class);

    private final SolicitudRepository solicitudRepository;
    private final SolicitudHistorialRepository historialRepository;
    private final VacacionesService vacacionesService;
    private final DiasHabilesService diasHabilesService;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;
    private final boolean coordinadorSaltaPrimeraInstancia;

    public SolicitudWorkflowService(
            SolicitudRepository solicitudRepository,
            SolicitudHistorialRepository historialRepository,
            VacacionesService vacacionesService,
            DiasHabilesService diasHabilesService,
            DocumentoAlmacenamiento almacenamiento,
            CapacidadService capacidades,
            @Value("${app.solicitudes.coordinador-salta-primera-instancia:true}")
            boolean coordinadorSaltaPrimeraInstancia) {
        this.solicitudRepository = solicitudRepository;
        this.historialRepository = historialRepository;
        this.vacacionesService = vacacionesService;
        this.diasHabilesService = diasHabilesService;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
        this.coordinadorSaltaPrimeraInstancia = coordinadorSaltaPrimeraInstancia;
    }

    // ── Creación ─────────────────────────────────────────────────────────────

    /**
     * Crea la solicitud en BORRADOR, con los días hábiles ya calculados.
     *
     * <p>El coordinador que decidirá se resuelve aquí y se congela: si la persona cambia
     * de jefe a mitad de trámite, la solicitud sigue en la bandeja de quien la tenía.</p>
     */
    @Transactional
    public Solicitud crearBorrador(Usuario solicitante, SolicitudTipo tipo,
                                   LocalDate inicio, LocalDate fin, String motivo) {
        return crearBorrador(solicitante, tipo, inicio, fin, motivo, null);
    }

    /** Igual, con un soporte opcional (p.ej. una excusa médica) adjunto de una vez. */
    @Transactional
    public Solicitud crearBorrador(Usuario solicitante, SolicitudTipo tipo,
                                   LocalDate inicio, LocalDate fin, String motivo, MultipartFile soporte) {

        if (fin.isBefore(inicio)) {
            throw new TransicionInvalidaException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        BigDecimal dias = diasHabilesService.contar(inicio, fin);
        if (dias.signum() <= 0) {
            throw new TransicionInvalidaException(
                    "El rango elegido no tiene ningún día hábil. Revisa las fechas.");
        }

        Solicitud solicitud = new Solicitud();
        solicitud.setId(UuidV7.nuevoId());
        solicitud.setTipo(tipo);
        solicitud.setSolicitante(solicitante);
        solicitud.setEstado(EstadoSolicitud.BORRADOR);
        solicitud.setFechaInicio(inicio);
        solicitud.setFechaFin(fin);
        solicitud.setDiasHabiles(dias);
        solicitud.setMotivo(motivo);
        solicitud.setCoordinadorAsignado(resolverCoordinador(solicitante));
        if (soporte != null && !soporte.isEmpty()) {
            subirSoporte(solicitud, soporte);
        }

        solicitudRepository.save(solicitud);
        anotar(solicitud, null, EstadoSolicitud.BORRADOR, solicitante, "Solicitud creada");
        return solicitud;
    }

    /** El adjunto de una solicitud: solo lo ve el solicitante, su coordinador asignado o Talento Humano. */
    @Transactional(readOnly = true)
    public ArchivoDescargado descargarSoporte(Usuario actor, String solicitudId) {
        Solicitud solicitud = cargar(solicitudId);
        boolean esSolicitante = solicitud.getSolicitante().getId().equals(actor.getId());
        boolean esCoordinadorAsignado = solicitud.getCoordinadorAsignado() != null
                && solicitud.getCoordinadorAsignado().getId().equals(actor.getId());
        boolean esTalentoHumano = capacidades.puede(Capacidades.SOLICITUD_APROBAR_SEGUNDA);
        if (!esSolicitante && !esCoordinadorAsignado && !esTalentoHumano) {
            throw new NoAutorizadoADecidirException("No puedes ver el adjunto de esa solicitud.");
        }
        if (solicitud.getArchivoClave() == null) {
            throw new TransicionInvalidaException("Esa solicitud no tiene ningún archivo adjunto.");
        }
        return new ArchivoDescargado(almacenamiento.descargar(solicitud.getArchivoClave()),
                solicitud.getArchivoNombreOriginal(), solicitud.getArchivoMime(),
                solicitud.getArchivoTamanoBytes() == null ? 0 : solicitud.getArchivoTamanoBytes());
    }

    /**
     * Envía el borrador a aprobación.
     *
     * <p>Aquí se decide por qué instancia entra. Si el solicitante no tiene coordinador
     * —porque él mismo lo es, o porque el organigrama no lo tiene asignado— entra directo
     * a Talento Humano: dejarlo en manos de un coordinador inexistente lo dejaría atascado
     * para siempre.</p>
     */
    @Transactional
    public Solicitud enviar(String solicitudId, Usuario actor) {
        Solicitud solicitud = cargar(solicitudId);

        if (!solicitud.getSolicitante().getId().equals(actor.getId())) {
            throw new NoAutorizadoADecidirException("Solo el solicitante puede enviar su solicitud.");
        }

        EstadoSolicitud destino = solicitud.getCoordinadorAsignado() == null
                ? EstadoSolicitud.PENDIENTE_SUPERVISOR
                : EstadoSolicitud.PENDIENTE_COORDINADOR;

        exigirTransicion(solicitud, destino);

        // Reservar ANTES de mover el estado: si no hay saldo, la solicitud no se envía.
        if (solicitud.esDeVacaciones()) {
            vacacionesService.reservar(solicitud);
        }

        mover(solicitud, destino, actor,
                destino == EstadoSolicitud.PENDIENTE_SUPERVISOR
                        ? "Enviada directamente a Talento Humano (sin coordinador asignado)"
                        : "Enviada a aprobación del coordinador");
        return solicitud;
    }

    // ── Primera instancia: el coordinador ────────────────────────────────────

    @Transactional
    public Solicitud aprobarCoordinador(String solicitudId, Usuario actor, String comentario) {
        Solicitud solicitud = cargar(solicitudId);
        exigirCoordinadorAsignado(solicitud, actor);
        exigirTransicion(solicitud, EstadoSolicitud.PENDIENTE_SUPERVISOR);

        solicitud.setDecididoCoordPor(actor);
        solicitud.setDecididoCoordEn(LocalDateTime.now());
        mover(solicitud, EstadoSolicitud.PENDIENTE_SUPERVISOR, actor, comentario);
        return solicitud;
    }

    /** Deniega en primera instancia. El flujo TERMINA aquí: nunca llega a RRHH (§4.4). */
    @Transactional
    public Solicitud rechazarCoordinador(String solicitudId, Usuario actor, String motivo) {
        exigirMotivo(motivo);
        Solicitud solicitud = cargar(solicitudId);
        exigirCoordinadorAsignado(solicitud, actor);
        exigirTransicion(solicitud, EstadoSolicitud.RECHAZADO_COORD);

        solicitud.setDecididoCoordPor(actor);
        solicitud.setDecididoCoordEn(LocalDateTime.now());

        if (solicitud.esDeVacaciones()) {
            vacacionesService.revertirReserva(solicitud, actor, "Denegada por el coordinador");
        }
        mover(solicitud, EstadoSolicitud.RECHAZADO_COORD, actor, motivo);
        return solicitud;
    }

    // ── Segunda instancia: Talento Humano ────────────────────────────────────

    @Transactional
    public Solicitud aprobarSupervisor(String solicitudId, Usuario actor, String comentario) {
        Solicitud solicitud = cargar(solicitudId);
        exigirQueNoSeaElSolicitante(solicitud, actor);
        exigirTransicion(solicitud, EstadoSolicitud.APROBADO);

        solicitud.setDecididoRrhhPor(actor);
        solicitud.setDecididoRrhhEn(LocalDateTime.now());

        if (solicitud.esDeVacaciones()) {
            vacacionesService.consumir(solicitud, actor);
        }
        mover(solicitud, EstadoSolicitud.APROBADO, actor, comentario);
        return solicitud;
    }

    /** Deniega en segunda instancia. El permiso NO queda validado. Estado final (§4.4). */
    @Transactional
    public Solicitud rechazarSupervisor(String solicitudId, Usuario actor, String motivo) {
        exigirMotivo(motivo);
        Solicitud solicitud = cargar(solicitudId);
        exigirQueNoSeaElSolicitante(solicitud, actor);
        exigirTransicion(solicitud, EstadoSolicitud.RECHAZADO_RRHH);

        solicitud.setDecididoRrhhPor(actor);
        solicitud.setDecididoRrhhEn(LocalDateTime.now());

        if (solicitud.esDeVacaciones()) {
            vacacionesService.revertirReserva(solicitud, actor, "Denegada por Talento Humano");
        }
        mover(solicitud, EstadoSolicitud.RECHAZADO_RRHH, actor, motivo);
        return solicitud;
    }

    // ── Reglas ───────────────────────────────────────────────────────────────

    /**
     * Quién aprueba en primera instancia.
     *
     * <p>Devuelve NULL —y entonces la solicitud entra directa a Talento Humano— cuando el
     * solicitante no tiene coordinador, o cuando lo tiene pero él mismo es coordinador y
     * la configuración dice que se salta la primera instancia.</p>
     *
     * <p><strong>Regla pendiente de confirmar (§4.4).</strong> El prompt propone que un
     * coordinador que pide permiso entre directamente en PENDIENTE_SUPERVISOR y pide
     * confirmarlo. Se implementa así, pero detrás de
     * {@code app.solicitudes.coordinador-salta-primera-instancia}: cambiarlo a false es
     * una línea de configuración, no una migración.</p>
     */
    private Usuario resolverCoordinador(Usuario solicitante) {
        Usuario coordinador = solicitante.getCoordinador();
        if (coordinador == null) {
            return null;
        }
        // Nadie se aprueba a sí mismo, ni siquiera por un organigrama mal cargado.
        if (coordinador.getId().equals(solicitante.getId())) {
            return null;
        }
        if (coordinadorSaltaPrimeraInstancia && esCoordinador(solicitante)) {
            return null;
        }
        return coordinador;
    }

    private boolean esCoordinador(Usuario usuario) {
        return usuario.getRoles().stream()
                .anyMatch(rol -> com.schoolplus.model.Rol.COORDINADOR.equals(rol.getCodigo()));
    }

    private void exigirCoordinadorAsignado(Solicitud solicitud, Usuario actor) {
        exigirQueNoSeaElSolicitante(solicitud, actor);
        Usuario asignado = solicitud.getCoordinadorAsignado();
        if (asignado == null || !asignado.getId().equals(actor.getId())) {
            throw new NoAutorizadoADecidirException(
                    "Esta solicitud la aprueba el coordinador del área del solicitante, no tú.");
        }
    }

    private void exigirQueNoSeaElSolicitante(Solicitud solicitud, Usuario actor) {
        if (solicitud.getSolicitante().getId().equals(actor.getId())) {
            throw new NoAutorizadoADecidirException(
                    "No puedes decidir sobre tu propia solicitud.");
        }
    }

    private void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new TransicionInvalidaException("Denegar exige escribir un motivo.");
        }
    }

    private void exigirTransicion(Solicitud solicitud, EstadoSolicitud destino) {
        if (!solicitud.getEstado().puedePasarA(destino)) {
            throw new TransicionInvalidaException(solicitud.getEstado(), destino);
        }
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void mover(Solicitud solicitud, EstadoSolicitud destino, Usuario actor, String comentario) {
        EstadoSolicitud origen = solicitud.getEstado();
        solicitud.setEstado(destino);
        solicitudRepository.save(solicitud);
        anotar(solicitud, origen, destino, actor, comentario);
        log.info("Solicitud {} : {} -> {} por {}", solicitud.getId(), origen, destino,
                actor == null ? "sistema" : actor.getEmail());
    }

    private void anotar(Solicitud solicitud, EstadoSolicitud origen, EstadoSolicitud destino,
                        Usuario actor, String comentario) {
        SolicitudHistorial linea = new SolicitudHistorial();
        linea.setSolicitud(solicitud);
        linea.setEstadoOrigen(origen);
        linea.setEstadoDestino(destino);
        linea.setActor(actor);
        linea.setComentario(comentario);
        historialRepository.save(linea);
    }

    private Solicitud cargar(String solicitudId) {
        return solicitudRepository.buscarCompleta(solicitudId)
                .orElseThrow(() -> new TransicionInvalidaException(
                        "La solicitud " + solicitudId + " no existe."));
    }

    private void subirSoporte(Solicitud solicitud, MultipartFile soporte) {
        ArchivoValidado validado = PoliticaArchivos.validar(soporte, TipoArchivo.SOPORTE);
        try {
            almacenamiento.subir(validado.clave(), soporte.getInputStream(), soporte.getSize(), validado.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo adjunto.", e);
        }
        solicitud.setArchivoClave(validado.clave());
        solicitud.setArchivoNombreOriginal(PoliticaArchivos.nombreParaMostrar(soporte.getOriginalFilename()));
        solicitud.setArchivoMime(validado.mime());
        solicitud.setArchivoTamanoBytes(soporte.getSize());
    }

    /** Para el llamador: recupera una solicitud ya decidida por su clave de idempotencia. */
    @Transactional(readOnly = true)
    public Optional<Solicitud> porClaveDeIdempotencia(String clave) {
        return clave == null ? Optional.empty() : solicitudRepository.findByIdempotencyKey(clave);
    }
}
