package com.schoolplus.services.muro;

import com.schoolplus.dto.EntradaMuro;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Comentario;
import com.schoolplus.model.EncuestaOpcion;
import com.schoolplus.model.EncuestaVoto;
import com.schoolplus.model.EstadoComentario;
import com.schoolplus.model.EstadoPublicacion;
import com.schoolplus.model.Publicacion;
import com.schoolplus.model.Reaccion;
import com.schoolplus.model.TipoPublicacion;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.ComentarioRepository;
import com.schoolplus.repository.EncuestaVotoRepository;
import com.schoolplus.repository.PublicacionRepository;
import com.schoolplus.repository.ReaccionRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El muro: publicaciones, encuestas, comentarios y reacciones (§4.7).
 *
 * <p>Quién puede hacer qué no se decide aquí con {@code if}s por rol: se pregunta a la
 * matriz de permisos, igual que en el resto del sistema. ADMIN y SUPERVISOR publican y
 * moderan; COORDINADOR y USUARIO comentan y reaccionan.</p>
 */
@Service
public class MuroService {

    private static final int POR_PAGINA = 20;

    private final PublicacionRepository publicacionRepository;
    private final ComentarioRepository comentarioRepository;
    private final ReaccionRepository reaccionRepository;
    private final EncuestaVotoRepository votoRepository;
    private final CapacidadService capacidades;
    private final NotificacionService notificacionService;

    public MuroService(PublicacionRepository publicacionRepository,
                       ComentarioRepository comentarioRepository,
                       ReaccionRepository reaccionRepository,
                       EncuestaVotoRepository votoRepository,
                       CapacidadService capacidades,
                       NotificacionService notificacionService) {
        this.publicacionRepository = publicacionRepository;
        this.comentarioRepository = comentarioRepository;
        this.reaccionRepository = reaccionRepository;
        this.votoRepository = votoRepository;
        this.capacidades = capacidades;
        this.notificacionService = notificacionService;
    }

    // ── Feed ─────────────────────────────────────────────────────────────────

    /**
     * El feed paginado, con recuentos resueltos en bloque.
     *
     * <p>Cinco consultas en total, sean 1 o 20 publicaciones: el feed, sus comentarios,
     * los recuentos de reacciones, las reacciones propias y los votos. La alternativa
     * —resolverlo por publicación— escala con lo que se pinta, que es la definición del
     * problema N+1.</p>
     */
    @Transactional(readOnly = true)
    public Page<EntradaMuro> feed(Usuario actor, TipoPublicacion filtro, int pagina) {
        boolean modera = capacidades.puede(Capacidades.PORTAL_MODERAR);

        Page<Publicacion> publicaciones = publicacionRepository.feed(
                modera, filtro, PageRequest.of(Math.max(0, pagina), POR_PAGINA));

        List<String> ids = publicaciones.getContent().stream().map(Publicacion::getId).toList();
        if (ids.isEmpty()) {
            return publicaciones.map(p -> new EntradaMuro(p, List.of(), 0, false, List.of(), null, modera));
        }

        Map<String, List<Comentario>> comentarios = comentarioRepository.deLasPublicaciones(ids).stream()
                .filter(c -> modera || c.estaVisible())
                .collect(Collectors.groupingBy(c -> c.getPublicacion().getId()));

        Map<String, Long> reacciones = aMapa(reaccionRepository.contarPorPublicacion(ids));
        Set<String> mias = Set.copyOf(reaccionRepository.misReacciones(actor.getId(), ids));
        Map<String, Long> votosPorOpcion = aMapa(votoRepository.contarPorOpcion(ids));
        Map<String, String> misVotos = votoRepository.misVotos(actor.getId(), ids).stream()
                .collect(Collectors.toMap(f -> (String) f[0], f -> (String) f[1]));

        return publicaciones.map(p -> new EntradaMuro(
                p,
                comentarios.getOrDefault(p.getId(), List.of()),
                reacciones.getOrDefault(p.getId(), 0L),
                mias.contains(p.getId()),
                resultados(p, votosPorOpcion),
                misVotos.get(p.getId()),
                modera));
    }

    // ── Publicar ─────────────────────────────────────────────────────────────

    @Transactional
    public Publicacion publicar(Usuario autor, TipoPublicacion tipo, String titulo, String cuerpo,
                                Usuario destinatario, List<String> opcionesEncuesta) {
        exigir(Capacidades.PORTAL_PUBLICAR, "No tienes permiso para publicar en el muro.");
        if (tipo == TipoPublicacion.ENCUESTA) {
            exigir(Capacidades.PORTAL_ENCUESTA_CREAR, "No tienes permiso para crear encuestas.");
        }
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new IllegalArgumentException("La publicación no puede ir vacía.");
        }

        Publicacion publicacion = new Publicacion();
        publicacion.setId(UuidV7.nuevoId());
        publicacion.setAutor(autor);
        publicacion.setTipo(tipo);
        publicacion.setTitulo(Textos.vacioANulo(titulo));
        publicacion.setCuerpo(cuerpo.trim());
        publicacion.setDestinatario(tipo == TipoPublicacion.RECONOCIMIENTO ? destinatario : null);

        if (tipo == TipoPublicacion.ENCUESTA) {
            List<String> limpias = opcionesEncuesta == null ? List.of() : opcionesEncuesta.stream()
                    .map(o -> o == null ? "" : o.trim())
                    .filter(o -> !o.isEmpty())
                    .toList();
            if (limpias.size() < 2) {
                throw new IllegalArgumentException("Una encuesta necesita al menos dos opciones.");
            }
            if (limpias.size() > 8) {
                throw new IllegalArgumentException("Una encuesta admite máximo ocho opciones.");
            }
            for (int i = 0; i < limpias.size(); i++) {
                EncuestaOpcion opcion = new EncuestaOpcion();
                opcion.setId(UuidV7.nuevoId());
                opcion.setPublicacion(publicacion);
                opcion.setTexto(limpias.get(i));
                opcion.setOrden(i);
                publicacion.getOpciones().add(opcion);
            }
        }

        return publicacionRepository.save(publicacion);
    }

    // ── Interacción ──────────────────────────────────────────────────────────

    @Transactional
    public Comentario comentar(Usuario autor, String publicacionId, String cuerpo) {
        exigir(Capacidades.PORTAL_COMENTAR, "No tienes permiso para comentar.");
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new IllegalArgumentException("El comentario no puede ir vacío.");
        }

        Publicacion publicacion = visible(publicacionId);

        Comentario comentario = new Comentario();
        comentario.setId(UuidV7.nuevoId());
        comentario.setPublicacion(publicacion);
        comentario.setAutor(autor);
        comentario.setCuerpo(cuerpo.trim());
        comentarioRepository.save(comentario);

        avisarAlAutor(publicacion, autor, autor.getNombre() + " comentó tu publicación",
                cuerpo.trim());
        return comentario;
    }

    /**
     * Pone o quita el "me gusta". Es un interruptor, no un contador.
     *
     * <p>La restricción única (publicacion_id, usuario_id) es la que impide que dos clics
     * simultáneos dejen dos reacciones de la misma persona. Aquí no hace falta
     * {@code @Version}: no se modifica una fila, se crea o se borra entera.</p>
     */
    @Transactional
    public boolean alternarReaccion(Usuario actor, String publicacionId) {
        exigir(Capacidades.PORTAL_COMENTAR, "No tienes permiso para reaccionar.");
        Publicacion publicacion = visible(publicacionId);

        var existente = reaccionRepository.findByPublicacionIdAndUsuarioId(publicacionId, actor.getId());
        if (existente.isPresent()) {
            reaccionRepository.delete(existente.get());
            return false;
        }

        Reaccion reaccion = new Reaccion();
        reaccion.setId(UuidV7.nuevoId());
        reaccion.setPublicacion(publicacion);
        reaccion.setUsuario(actor);
        reaccionRepository.save(reaccion);

        avisarAlAutor(publicacion, actor, actor.getNombre() + " reaccionó a tu publicación", null);
        return true;
    }

    /** Vota, o cambia el voto. Una persona, una opción por encuesta. */
    @Transactional
    public void votar(Usuario actor, String publicacionId, String opcionId) {
        exigir(Capacidades.PORTAL_COMENTAR, "No tienes permiso para votar.");

        Publicacion publicacion = visible(publicacionId);
        if (!publicacion.admiteVotos()) {
            throw new IllegalArgumentException("Esta encuesta ya está cerrada.");
        }

        EncuestaOpcion opcion = publicacion.getOpciones().stream()
                .filter(o -> o.getId().equals(opcionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esa opción no es de esta encuesta."));

        EncuestaVoto voto = votoRepository
                .findByPublicacionIdAndUsuarioId(publicacionId, actor.getId())
                .orElseGet(() -> {
                    EncuestaVoto nuevo = new EncuestaVoto();
                    nuevo.setId(UuidV7.nuevoId());
                    nuevo.setPublicacion(publicacion);
                    nuevo.setUsuario(actor);
                    return nuevo;
                });
        voto.setOpcion(opcion);
        votoRepository.save(voto);
    }

    // ── Moderación ───────────────────────────────────────────────────────────

    @Transactional
    public void alternarVisibilidad(Usuario actor, String publicacionId) {
        exigir(Capacidades.PORTAL_MODERAR, "No tienes permiso para moderar el muro.");

        Publicacion publicacion = publicacionRepository.buscarConAutor(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Esa publicación no existe."));

        if (publicacion.estaVisible()) {
            publicacion.setEstado(EstadoPublicacion.OCULTA);
            publicacion.setOcultaPor(actor);
            publicacion.setOcultaEn(LocalDateTime.now());
        } else {
            publicacion.setEstado(EstadoPublicacion.VISIBLE);
            publicacion.setOcultaPor(null);
            publicacion.setOcultaEn(null);
        }
        publicacionRepository.save(publicacion);
    }

    @Transactional
    public void alternarFijada(Usuario actor, String publicacionId) {
        exigir(Capacidades.PORTAL_PUBLICAR, "No tienes permiso para fijar publicaciones.");

        Publicacion publicacion = publicacionRepository.buscarConAutor(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Esa publicación no existe."));
        publicacion.setFijada(!publicacion.isFijada());
        publicacionRepository.save(publicacion);
    }

    @Transactional
    public void ocultarComentario(Usuario actor, String comentarioId) {
        exigir(Capacidades.PORTAL_MODERAR, "No tienes permiso para moderar comentarios.");

        Comentario comentario = comentarioRepository.buscarConAutor(comentarioId)
                .orElseThrow(() -> new IllegalArgumentException("Ese comentario no existe."));

        boolean visible = comentario.estaVisible();
        comentario.setEstado(visible ? EstadoComentario.OCULTO : EstadoComentario.VISIBLE);
        comentario.setOcultoPor(visible ? actor : null);
        comentario.setOcultoEn(visible ? LocalDateTime.now() : null);
        comentarioRepository.save(comentario);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private List<EntradaMuro.OpcionEncuesta> resultados(Publicacion publicacion,
                                                        Map<String, Long> votosPorOpcion) {
        if (!publicacion.esEncuesta()) {
            return List.of();
        }
        long total = publicacion.getOpciones().stream()
                .mapToLong(o -> votosPorOpcion.getOrDefault(o.getId(), 0L))
                .sum();

        List<EntradaMuro.OpcionEncuesta> resultado = new ArrayList<>();
        for (EncuestaOpcion opcion : publicacion.getOpciones()) {
            long votos = votosPorOpcion.getOrDefault(opcion.getId(), 0L);
            // El porcentaje se calcula aquí: dejarlo a la plantilla es pedir una división
            // por cero el día que nadie haya votado todavía.
            int porcentaje = total == 0 ? 0 : (int) Math.round(votos * 100.0 / total);
            resultado.add(new EntradaMuro.OpcionEncuesta(
                    opcion.getId(), opcion.getTexto(), votos, porcentaje));
        }
        return resultado;
    }

    /** Una sola entrada, ya resuelta, para repintar tras una interacción por AJAX. */
    @Transactional(readOnly = true)
    public EntradaMuro entradaDe(Usuario actor, String publicacionId) {
        boolean modera = capacidades.puede(Capacidades.PORTAL_MODERAR);
        Publicacion publicacion = publicacionRepository.buscarConAutor(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Esa publicación no existe."));

        List<String> ids = List.of(publicacionId);
        List<Comentario> comentarios = comentarioRepository.deLasPublicaciones(ids).stream()
                .filter(c -> modera || c.estaVisible())
                .toList();
        long reacciones = reaccionRepository.contarPorPublicacion(ids).stream()
                .findFirst().map(f -> ((Number) f[1]).longValue()).orElse(0L);
        boolean yaReaccione = reaccionRepository.misReacciones(actor.getId(), ids).contains(publicacionId);
        Map<String, Long> votosPorOpcion = aMapa(votoRepository.contarPorOpcion(ids));
        String miOpcion = votoRepository.misVotos(actor.getId(), ids).stream()
                .findFirst().map(f -> (String) f[1]).orElse(null);

        return new EntradaMuro(publicacion, comentarios, reacciones, yaReaccione,
                resultados(publicacion, votosPorOpcion), miOpcion, modera);
    }

    /** Avisa al dueño de la publicación, salvo que sea quien acaba de interactuar con la suya. */
    private void avisarAlAutor(Publicacion publicacion, Usuario actor, String titulo, String cuerpo) {
        Usuario autorPublicacion = publicacion.getAutor();
        if (autorPublicacion.getId().equals(actor.getId())) {
            return;
        }
        notificacionService.crear(autorPublicacion.getId(), "MURO_INTERACCION", titulo, cuerpo, "/portal");
    }

    private Publicacion visible(String publicacionId) {
        Publicacion publicacion = publicacionRepository.buscarConAutor(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Esa publicación no existe."));
        if (!publicacion.estaVisible()) {
            throw new IllegalArgumentException("Esa publicación está oculta.");
        }
        return publicacion;
    }

    private void exigir(String capacidad, String mensaje) {
        if (!capacidades.puede(capacidad)) {
            throw new org.springframework.security.access.AccessDeniedException(mensaje);
        }
    }

    private static Map<String, Long> aMapa(List<Object[]> filas) {
        Map<String, Long> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            mapa.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }
}
