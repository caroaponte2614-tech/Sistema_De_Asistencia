package com.schoolplus.services.capacitacion;

import com.schoolplus.dto.CursoDetalle;
import com.schoolplus.dto.OpcionEntrada;
import com.schoolplus.dto.OpcionVista;
import com.schoolplus.dto.PreguntaEntrada;
import com.schoolplus.dto.PreguntaVista;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Curso;
import com.schoolplus.model.CursoInscripcion;
import com.schoolplus.model.CursoIntento;
import com.schoolplus.model.CursoIntentoRespuesta;
import com.schoolplus.model.CursoOpcion;
import com.schoolplus.model.CursoPregunta;
import com.schoolplus.model.TipoCurso;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CursoInscripcionRepository;
import com.schoolplus.repository.CursoIntentoRepository;
import com.schoolplus.repository.CursoIntentoRespuestaRepository;
import com.schoolplus.repository.CursoOpcionRepository;
import com.schoolplus.repository.CursoPreguntaRepository;
import com.schoolplus.repository.CursoRepository;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.documentos.PoliticaArchivos;
import com.schoolplus.services.documentos.PoliticaArchivos.ArchivoValidado;
import com.schoolplus.services.documentos.TipoArchivo;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capacitaciones: un curso es un video más, opcionalmente, un quiz. La inducción no es
 * un módulo aparte — es un curso con {@link TipoCurso#INDUCCION}, ver {@link Curso}.
 *
 * <p>Sin máquina de aprobación: Talento Humano administra el catálogo directamente
 * (mismo espíritu que {@code DocumentoService.subirRestringido}), y cada colaborador
 * avanza a su ritmo sobre su propia {@link CursoInscripcion}.</p>
 */
@Service
public class CursoService {

    private static final Logger log = LoggerFactory.getLogger(CursoService.class);

    /** Nota mínima para aprobar el quiz, sobre 100. No viene especificada en el
     * requerimiento — se documenta acá como el único lugar que hay que tocar si
     * Talento Humano define otra cosa. */
    private static final BigDecimal NOTA_MINIMA_APROBACION = new BigDecimal("70.00");

    private final CursoRepository cursoRepository;
    private final CursoPreguntaRepository preguntaRepository;
    private final CursoOpcionRepository opcionRepository;
    private final CursoInscripcionRepository inscripcionRepository;
    private final CursoIntentoRepository intentoRepository;
    private final CursoIntentoRespuestaRepository intentoRespuestaRepository;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;

    public CursoService(CursoRepository cursoRepository,
                        CursoPreguntaRepository preguntaRepository,
                        CursoOpcionRepository opcionRepository,
                        CursoInscripcionRepository inscripcionRepository,
                        CursoIntentoRepository intentoRepository,
                        CursoIntentoRespuestaRepository intentoRespuestaRepository,
                        DocumentoAlmacenamiento almacenamiento,
                        CapacidadService capacidades) {
        this.cursoRepository = cursoRepository;
        this.preguntaRepository = preguntaRepository;
        this.opcionRepository = opcionRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.intentoRepository = intentoRepository;
        this.intentoRespuestaRepository = intentoRespuestaRepository;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
    }

    // ── Catálogo (colaborador) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Curso> catalogo() {
        return cursoRepository.findByPublicadoTrueAndTipoOrderByOrdenAscCreadoEnDesc(TipoCurso.CAPACITACION);
    }

    @Transactional(readOnly = true)
    public Optional<Curso> cursoInduccionObligatorio() {
        return cursoRepository.findFirstByPublicadoTrueAndTipoAndObligatorioTrueOrderByCreadoEnDesc(TipoCurso.INDUCCION);
    }

    @Transactional(readOnly = true)
    public CursoDetalle detalle(Usuario colaborador, String cursoId) {
        Curso curso = cursoRepository.buscarCompleto(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));

        List<PreguntaVista> preguntas = preguntaRepository.findByCursoIdOrderByOrdenAsc(cursoId).stream()
                .map(this::sinRespuesta)
                .toList();

        CursoInscripcion inscripcion = inscripcionRepository
                .findByCursoIdAndUsuarioId(cursoId, colaborador.getId())
                .orElse(null);

        return new CursoDetalle(curso, preguntas, inscripcion);
    }

    @Transactional(readOnly = true)
    public List<CursoInscripcion> misInscripciones(Usuario colaborador) {
        return inscripcionRepository.misInscripciones(colaborador.getId());
    }

    /** Metadatos del video (tamaño, mime) — el controlador los necesita para armar la
     * respuesta 206 antes de leer un solo byte. */
    @Transactional(readOnly = true)
    public Curso obtenerParaVideo(String cursoId) {
        return cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));
    }

    /** El almacenamiento queda encapsulado acá: el controlador nunca lee el disco directo. */
    @Transactional(readOnly = true)
    public java.io.InputStream streamVideo(Curso curso, Long offset, Long length) {
        return almacenamiento.descargar(curso.getVideoClave(), offset, length);
    }

    /** Miniatura de la tarjeta (catálogo y carrusel del portal). Sin miniatura, 404. */
    @Transactional(readOnly = true)
    public com.schoolplus.services.documentos.ArchivoDescargado miniatura(String cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));
        if (curso.getMiniaturaClave() == null) {
            throw new IllegalArgumentException("Ese curso no tiene miniatura.");
        }
        var info = almacenamiento.info(curso.getMiniaturaClave());
        return new com.schoolplus.services.documentos.ArchivoDescargado(
                almacenamiento.descargar(curso.getMiniaturaClave()), "miniatura", info.mime(), info.tamanoBytes());
    }

    // ── Administración (Talento Humano) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Curso> listarTodos() {
        exigirCapacidadAdministrar();
        return cursoRepository.findAllByOrderByCreadoEnDesc();
    }

    @Transactional(readOnly = true)
    public Curso obtenerParaAdministrar(String cursoId) {
        exigirCapacidadAdministrar();
        return cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));
    }

    /** El quiz actual, en el mismo formato de entrada — precarga el formulario de edición. */
    @Transactional(readOnly = true)
    public List<PreguntaEntrada> preguntasActuales(String cursoId) {
        exigirCapacidadAdministrar();
        return preguntaRepository.findByCursoIdOrderByOrdenAsc(cursoId).stream()
                .map(pregunta -> new PreguntaEntrada(pregunta.getEnunciado(), pregunta.getTipo(),
                        opcionRepository.findByPreguntaIdOrderByOrdenAsc(pregunta.getId()).stream()
                                .map(o -> new OpcionEntrada(o.getTexto(), o.isCorrecta()))
                                .toList()))
                .toList();
    }

    @Transactional
    public Curso crear(Usuario actor, String titulo, String descripcion, TipoCurso tipo,
                       boolean obligatorio, MultipartFile video) {
        return crear(actor, titulo, descripcion, tipo, obligatorio, video, null);
    }

    @Transactional
    public Curso crear(Usuario actor, String titulo, String descripcion, TipoCurso tipo,
                       boolean obligatorio, MultipartFile video, MultipartFile miniatura) {
        exigirCapacidadAdministrar();
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El curso necesita un título.");
        }
        if (video == null || video.isEmpty()) {
            throw new IllegalArgumentException("Elige un video antes de crear el curso.");
        }

        Curso curso = new Curso();
        curso.setId(UuidV7.nuevoId());
        curso.setTitulo(titulo);
        curso.setDescripcion(Textos.vacioANulo(descripcion));
        curso.setTipo(tipo == null ? TipoCurso.CAPACITACION : tipo);
        curso.setObligatorio(obligatorio);
        curso.setCreadoPor(actor);
        curso.setPublicado(false);
        subirVideo(curso, video);
        if (miniatura != null && !miniatura.isEmpty()) {
            subirMiniatura(curso, miniatura);
        }

        cursoRepository.save(curso);
        log.info("Curso creado: {} ({}) por {}", curso.getTitulo(), curso.getId(), actor.getEmail());
        return curso;
    }

    @Transactional
    public Curso actualizar(Usuario actor, String cursoId, String titulo, String descripcion,
                            boolean obligatorio, MultipartFile nuevoVideo, MultipartFile nuevaMiniatura) {
        exigirCapacidadAdministrar();
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));

        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El curso necesita un título.");
        }
        curso.setTitulo(titulo);
        curso.setDescripcion(Textos.vacioANulo(descripcion));
        curso.setObligatorio(obligatorio);

        if (nuevoVideo != null && !nuevoVideo.isEmpty()) {
            subirVideo(curso, nuevoVideo);
        }
        if (nuevaMiniatura != null && !nuevaMiniatura.isEmpty()) {
            subirMiniatura(curso, nuevaMiniatura);
        }

        cursoRepository.save(curso);
        log.info("Curso {} actualizado por {}", cursoId, actor.getEmail());
        return curso;
    }

    @Transactional
    public Curso publicar(Usuario actor, String cursoId, boolean publicado) {
        exigirCapacidadAdministrar();
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));
        curso.setPublicado(publicado);
        cursoRepository.save(curso);
        log.info("Curso {} {} por {}", cursoId, publicado ? "publicado" : "despublicado", actor.getEmail());
        return curso;
    }

    /**
     * Borra y recrea el quiz completo — más simple que llevar el control de qué
     * pregunta es nueva, cuál cambió y cuál se borró. El costo de este camino queda
     * documentado en la migración V17: los intentos ya presentados pierden el detalle
     * pregunta-a-pregunta si el quiz cambia después, aunque conservan la nota.
     */
    @Transactional
    public void reemplazarPreguntas(Usuario actor, String cursoId, List<PreguntaEntrada> preguntas) {
        exigirCapacidadAdministrar();
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));

        preguntaRepository.deleteByCursoId(cursoId);

        int ordenPregunta = 0;
        for (PreguntaEntrada entradaPregunta : preguntas) {
            validarPregunta(entradaPregunta);

            CursoPregunta pregunta = new CursoPregunta();
            pregunta.setId(UuidV7.nuevoId());
            pregunta.setCurso(curso);
            pregunta.setEnunciado(entradaPregunta.enunciado());
            pregunta.setTipo(entradaPregunta.tipo());
            pregunta.setOrden(ordenPregunta++);
            preguntaRepository.save(pregunta);

            int ordenOpcion = 0;
            for (OpcionEntrada entradaOpcion : entradaPregunta.opciones()) {
                CursoOpcion opcion = new CursoOpcion();
                opcion.setId(UuidV7.nuevoId());
                opcion.setPregunta(pregunta);
                opcion.setTexto(entradaOpcion.texto());
                opcion.setCorrecta(entradaOpcion.correcta());
                opcion.setOrden(ordenOpcion++);
                opcionRepository.save(opcion);
            }
        }
        log.info("Quiz de curso {} reemplazado por {} ({} preguntas)", cursoId, actor.getEmail(), preguntas.size());
    }

    // ── Progreso (colaborador) ────────────────────────────────────────────────

    @Transactional
    public CursoInscripcion marcarVideoVisto(Usuario colaborador, String cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));

        CursoInscripcion inscripcion = inscripcionDe(curso, colaborador);
        if (inscripcion.getVideoCompletadoEn() == null) {
            inscripcion.setVideoCompletadoEn(LocalDateTime.now());
            inscripcionRepository.save(inscripcion);
        }
        return inscripcion;
    }

    /**
     * Califica el quiz server-side y actualiza el resumen de la inscripción. Exige
     * haber visto el video antes — no se puede saltar directo al quiz.
     *
     * @param respuestas preguntaId -> opcionElegidaId (puede faltar una pregunta, cuenta
     *                   como incorrecta).
     */
    @Transactional
    public CursoIntento presentarQuiz(Usuario colaborador, String cursoId, Map<String, String> respuestas) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Ese curso no existe."));

        CursoInscripcion inscripcion = inscripcionRepository
                .findByCursoIdAndUsuarioId(cursoId, colaborador.getId())
                .orElseThrow(() -> new IllegalArgumentException("Primero tienes que ver el video."));
        if (inscripcion.getVideoCompletadoEn() == null) {
            throw new IllegalArgumentException("Primero tienes que terminar de ver el video.");
        }

        List<CursoPregunta> preguntas = preguntaRepository.findByCursoIdOrderByOrdenAsc(cursoId);
        if (preguntas.isEmpty()) {
            throw new IllegalArgumentException("Este curso no tiene quiz.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        CursoIntento intento = new CursoIntento();
        intento.setId(UuidV7.nuevoId());
        intento.setInscripcion(inscripcion);
        intento.setIniciadoEn(ahora);
        intento.setTerminadoEn(ahora);

        // Se arma primero en memoria y se guarda el intento antes que las respuestas:
        // cada CursoIntentoRespuesta necesita que su fila padre ya exista en BD.
        List<CursoIntentoRespuesta> respuestasIntento = new java.util.ArrayList<>();
        int correctas = 0;
        for (CursoPregunta pregunta : preguntas) {
            String opcionElegidaId = respuestas == null ? null : respuestas.get(pregunta.getId());
            CursoOpcion opcionElegida = opcionElegidaId == null ? null
                    : opcionRepository.findById(opcionElegidaId).orElse(null);
            boolean esCorrecta = opcionElegida != null && opcionElegida.isCorrecta();
            if (esCorrecta) {
                correctas++;
            }

            CursoIntentoRespuesta respuesta = new CursoIntentoRespuesta();
            respuesta.setId(UuidV7.nuevoId());
            respuesta.setIntento(intento);
            respuesta.setPregunta(pregunta);
            respuesta.setOpcionElegida(opcionElegida);
            respuesta.setCorrecta(esCorrecta);
            respuestasIntento.add(respuesta);
        }

        BigDecimal nota = BigDecimal.valueOf(correctas)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(preguntas.size()), 2, RoundingMode.HALF_UP);
        boolean aprobado = nota.compareTo(NOTA_MINIMA_APROBACION) >= 0;
        intento.setNota(nota);
        intento.setAprobado(aprobado);
        intentoRepository.save(intento);
        respuestasIntento.forEach(intentoRespuestaRepository::save);

        actualizarResumen(inscripcion, nota, aprobado);
        return intento;
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private void actualizarResumen(CursoInscripcion inscripcion, BigDecimal nota, boolean aprobado) {
        inscripcion.setIntentos(inscripcion.getIntentos() + 1);
        if (inscripcion.getMejorNota() == null || nota.compareTo(inscripcion.getMejorNota()) > 0) {
            inscripcion.setMejorNota(nota);
        }
        if (aprobado) {
            inscripcion.setQuizAprobado(true);
        }
        if (inscripcion.isQuizAprobado() && inscripcion.getVideoCompletadoEn() != null
                && inscripcion.getCompletadoEn() == null) {
            inscripcion.setCompletadoEn(LocalDateTime.now());
        }
        inscripcionRepository.save(inscripcion);
    }

    private CursoInscripcion inscripcionDe(Curso curso, Usuario colaborador) {
        return inscripcionRepository.findByCursoIdAndUsuarioId(curso.getId(), colaborador.getId())
                .orElseGet(() -> {
                    CursoInscripcion nueva = new CursoInscripcion();
                    nueva.setId(UuidV7.nuevoId());
                    nueva.setCurso(curso);
                    nueva.setUsuario(colaborador);
                    return inscripcionRepository.save(nueva);
                });
    }

    private void validarPregunta(PreguntaEntrada entrada) {
        if (entrada.enunciado() == null || entrada.enunciado().isBlank()) {
            throw new IllegalArgumentException("Cada pregunta necesita un enunciado.");
        }
        if (entrada.opciones() == null || entrada.opciones().size() < 2) {
            throw new IllegalArgumentException("Cada pregunta necesita al menos dos opciones.");
        }
        long correctas = entrada.opciones().stream().filter(OpcionEntrada::correcta).count();
        if (correctas != 1) {
            throw new IllegalArgumentException(
                    "Cada pregunta necesita exactamente una opción marcada como correcta.");
        }
    }

    private PreguntaVista sinRespuesta(CursoPregunta pregunta) {
        List<OpcionVista> opciones = opcionRepository.findByPreguntaIdOrderByOrdenAsc(pregunta.getId()).stream()
                .map(o -> new OpcionVista(o.getId(), o.getTexto()))
                .toList();
        return new PreguntaVista(pregunta.getId(), pregunta.getEnunciado(), pregunta.getTipo(), opciones);
    }

    private void subirVideo(Curso curso, MultipartFile video) {
        ArchivoValidado validado = PoliticaArchivos.validar(video, TipoArchivo.VIDEO);
        try {
            almacenamiento.subir(validado.clave(), video.getInputStream(), video.getSize(), validado.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el video enviado.", e);
        }
        curso.setVideoClave(validado.clave());
        curso.setVideoMime(validado.mime());
        curso.setVideoTamanoBytes(video.getSize());
    }

    private void subirMiniatura(Curso curso, MultipartFile miniatura) {
        ArchivoValidado validado = PoliticaArchivos.validar(miniatura, TipoArchivo.IMAGEN);
        try {
            almacenamiento.subir(validado.clave(), miniatura.getInputStream(), miniatura.getSize(), validado.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen enviada.", e);
        }
        curso.setMiniaturaClave(validado.clave());
    }

    private void exigirCapacidadAdministrar() {
        if (!capacidades.puede(Capacidades.CAPACITACION_ADMINISTRAR)) {
            throw new IllegalArgumentException("No tienes permiso para administrar cursos.");
        }
    }
}
