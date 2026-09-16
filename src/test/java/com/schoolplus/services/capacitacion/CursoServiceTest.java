package com.schoolplus.services.capacitacion;

import com.schoolplus.dto.OpcionEntrada;
import com.schoolplus.dto.PreguntaEntrada;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.Curso;
import com.schoolplus.model.CursoInscripcion;
import com.schoolplus.model.CursoIntento;
import com.schoolplus.model.CursoOpcion;
import com.schoolplus.model.CursoPregunta;
import com.schoolplus.model.TipoCurso;
import com.schoolplus.model.TipoPregunta;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.CursoInscripcionRepository;
import com.schoolplus.repository.CursoIntentoRepository;
import com.schoolplus.repository.CursoIntentoRespuestaRepository;
import com.schoolplus.repository.CursoOpcionRepository;
import com.schoolplus.repository.CursoPreguntaRepository;
import com.schoolplus.repository.CursoRepository;
import com.schoolplus.services.documentos.DocumentoAlmacenamiento;
import com.schoolplus.services.permisos.CapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El motor de cursos: alta, quiz y el candado "primero el video, después el quiz". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CursoServiceTest {

    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private CursoPreguntaRepository preguntaRepository;
    @Mock
    private CursoOpcionRepository opcionRepository;
    @Mock
    private CursoInscripcionRepository inscripcionRepository;
    @Mock
    private CursoIntentoRepository intentoRepository;
    @Mock
    private CursoIntentoRespuestaRepository intentoRespuestaRepository;
    @Mock
    private DocumentoAlmacenamiento almacenamiento;
    @Mock
    private CapacidadService capacidadService;

    private CursoService service;

    private Usuario rrhh;
    private Usuario colaborador;
    private Curso curso;

    @BeforeEach
    void preparar() {
        service = new CursoService(cursoRepository, preguntaRepository, opcionRepository,
                inscripcionRepository, intentoRepository, intentoRespuestaRepository,
                almacenamiento, capacidadService);

        rrhh = new Usuario();
        rrhh.setId("rrhh-1");
        rrhh.setEmail("rrhh@schoolplus.example");

        colaborador = new Usuario();
        colaborador.setId("colab-1");
        colaborador.setEmail("colaborador@schoolplus.example");

        curso = new Curso();
        curso.setId("curso-1");
        curso.setTitulo("Manejo de extintores");
        curso.setTipo(TipoCurso.CAPACITACION);

        lenient().when(cursoRepository.save(any(Curso.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(inscripcionRepository.save(any(CursoInscripcion.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(intentoRepository.save(any(CursoIntento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Crear ────────────────────────────────────────────────────────────────

    @Test
    void crearFallaSinCapacidadDeAdministrar() {
        when(capacidadService.puede(Capacidades.CAPACITACION_ADMINISTRAR)).thenReturn(false);
        MockMultipartFile video = new MockMultipartFile("video", "induccion.mp4", "video/mp4", new byte[]{1});

        assertThatThrownBy(() -> service.crear(rrhh, "Inducción", null, TipoCurso.INDUCCION, true, video))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permiso");

        verify(cursoRepository, never()).save(any());
    }

    @Test
    void crearExigeUnVideo() {
        when(capacidadService.puede(Capacidades.CAPACITACION_ADMINISTRAR)).thenReturn(true);

        assertThatThrownBy(() -> service.crear(rrhh, "Inducción", null, TipoCurso.INDUCCION, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("video");
    }

    @Test
    void crearGuardaElCursoSinPublicarYSubeElVideo() throws Exception {
        when(capacidadService.puede(Capacidades.CAPACITACION_ADMINISTRAR)).thenReturn(true);
        MockMultipartFile video = new MockMultipartFile("video", "induccion.mp4", "video/mp4", new byte[]{1, 2, 3});

        Curso creado = service.crear(rrhh, "Inducción", "Bienvenida", TipoCurso.INDUCCION, true, video);

        assertThat(creado.getTitulo()).isEqualTo("Inducción");
        assertThat(creado.getTipo()).isEqualTo(TipoCurso.INDUCCION);
        assertThat(creado.isObligatorio()).isTrue();
        assertThat(creado.isPublicado()).isFalse();
        assertThat(creado.getCreadoPor()).isSameAs(rrhh);
        // La clave es <carpeta>/<uuid>.<ext>: el nombre original nunca llega al disco.
        assertThat(creado.getVideoClave()).startsWith("videos/").endsWith(".mp4").doesNotContain("induccion");
        verify(almacenamiento).subir(any(), any(), eq(3L), eq("video/mp4"));
    }

    // ── Quiz: armado ─────────────────────────────────────────────────────────

    @Test
    void reemplazarPreguntasExigeExactamenteUnaOpcionCorrecta() {
        when(capacidadService.puede(Capacidades.CAPACITACION_ADMINISTRAR)).thenReturn(true);
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));

        List<PreguntaEntrada> preguntas = List.of(new PreguntaEntrada("¿Dónde está el extintor?",
                TipoPregunta.OPCION_UNICA,
                List.of(new OpcionEntrada("En la cocina", true), new OpcionEntrada("En el techo", true))));

        assertThatThrownBy(() -> service.reemplazarPreguntas(rrhh, "curso-1", preguntas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactamente una opción");
    }

    @Test
    void reemplazarPreguntasBorraLasPreguntasViejasAntesDeCrearLasNuevas() {
        when(capacidadService.puede(Capacidades.CAPACITACION_ADMINISTRAR)).thenReturn(true);
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));

        List<PreguntaEntrada> preguntas = List.of(new PreguntaEntrada("¿Dónde está el extintor?",
                TipoPregunta.OPCION_UNICA,
                List.of(new OpcionEntrada("En la cocina", true), new OpcionEntrada("En el techo", false))));

        service.reemplazarPreguntas(rrhh, "curso-1", preguntas);

        verify(preguntaRepository).deleteByCursoId("curso-1");
        verify(preguntaRepository).save(any(CursoPregunta.class));
        verify(opcionRepository, org.mockito.Mockito.times(2)).save(any(CursoOpcion.class));
    }

    // ── Progreso: el video antes que el quiz ────────────────────────────────

    @Test
    void marcarVideoVistoCreaLaInscripcionLaPrimeraVez() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.empty());

        CursoInscripcion inscripcion = service.marcarVideoVisto(colaborador, "curso-1");

        assertThat(inscripcion.getVideoCompletadoEn()).isNotNull();
        assertThat(inscripcion.getCurso()).isSameAs(curso);
        assertThat(inscripcion.getUsuario()).isSameAs(colaborador);
    }

    @Test
    void marcarVideoVistoNoPisaLaFechaSiYaEstabaCompletado() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));
        CursoInscripcion existente = new CursoInscripcion();
        existente.setId("insc-1");
        existente.setCurso(curso);
        existente.setUsuario(colaborador);
        LocalDateTime primeraVez = LocalDateTime.now().minusDays(1);
        existente.setVideoCompletadoEn(primeraVez);
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.of(existente));

        CursoInscripcion resultado = service.marcarVideoVisto(colaborador, "curso-1");

        assertThat(resultado.getVideoCompletadoEn()).isEqualTo(primeraVez);
        verify(inscripcionRepository, never()).save(any());
    }

    @Test
    void presentarQuizFallaSiNuncaVioElVideo() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.presentarQuiz(colaborador, "curso-1", java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ver el video");
    }

    @Test
    void presentarQuizFallaSiElVideoNoEstaCompletado() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));
        CursoInscripcion inscripcion = new CursoInscripcion();
        inscripcion.setId("insc-1");
        inscripcion.setCurso(curso);
        inscripcion.setUsuario(colaborador);
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.of(inscripcion));

        assertThatThrownBy(() -> service.presentarQuiz(colaborador, "curso-1", java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminar de ver el video");
    }

    @Test
    void presentarQuizCalificaYApruebaConSetentaOMas() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));

        CursoInscripcion inscripcion = new CursoInscripcion();
        inscripcion.setId("insc-1");
        inscripcion.setCurso(curso);
        inscripcion.setUsuario(colaborador);
        inscripcion.setVideoCompletadoEn(LocalDateTime.now());
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.of(inscripcion));

        CursoPregunta p1 = pregunta("p1", 0);
        CursoPregunta p2 = pregunta("p2", 1);
        when(preguntaRepository.findByCursoIdOrderByOrdenAsc("curso-1")).thenReturn(List.of(p1, p2));

        CursoOpcion p1Correcta = opcion("p1-si", p1, true);
        CursoOpcion p2Correcta = opcion("p2-si", p2, true);
        when(opcionRepository.findById("p1-si")).thenReturn(Optional.of(p1Correcta));
        when(opcionRepository.findById("p2-si")).thenReturn(Optional.of(p2Correcta));

        CursoIntento intento = service.presentarQuiz(colaborador, "curso-1",
                java.util.Map.of("p1", "p1-si", "p2", "p2-si"));

        assertThat(intento.getNota()).isEqualByComparingTo("100.00");
        assertThat(intento.isAprobado()).isTrue();
        assertThat(inscripcion.isQuizAprobado()).isTrue();
        assertThat(inscripcion.getIntentos()).isEqualTo(1);
        assertThat(inscripcion.getCompletadoEn()).isNotNull();
    }

    @Test
    void presentarQuizNoApruebaPorDebajoDelUmbral() {
        when(cursoRepository.findById("curso-1")).thenReturn(Optional.of(curso));

        CursoInscripcion inscripcion = new CursoInscripcion();
        inscripcion.setId("insc-1");
        inscripcion.setCurso(curso);
        inscripcion.setUsuario(colaborador);
        inscripcion.setVideoCompletadoEn(LocalDateTime.now());
        when(inscripcionRepository.findByCursoIdAndUsuarioId("curso-1", "colab-1")).thenReturn(Optional.of(inscripcion));

        CursoPregunta p1 = pregunta("p1", 0);
        CursoPregunta p2 = pregunta("p2", 1);
        when(preguntaRepository.findByCursoIdOrderByOrdenAsc("curso-1")).thenReturn(List.of(p1, p2));

        CursoOpcion p1Correcta = opcion("p1-si", p1, true);
        CursoOpcion p2Incorrecta = opcion("p2-no", p2, false);
        when(opcionRepository.findById("p1-si")).thenReturn(Optional.of(p1Correcta));
        when(opcionRepository.findById("p2-no")).thenReturn(Optional.of(p2Incorrecta));

        CursoIntento intento = service.presentarQuiz(colaborador, "curso-1",
                java.util.Map.of("p1", "p1-si", "p2", "p2-no"));

        assertThat(intento.getNota()).isEqualByComparingTo("50.00");
        assertThat(intento.isAprobado()).isFalse();
        assertThat(inscripcion.isQuizAprobado()).isFalse();
        assertThat(inscripcion.getCompletadoEn()).isNull();
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private CursoPregunta pregunta(String id, int orden) {
        CursoPregunta pregunta = new CursoPregunta();
        pregunta.setId(id);
        pregunta.setCurso(curso);
        pregunta.setEnunciado("Pregunta " + id);
        pregunta.setTipo(TipoPregunta.OPCION_UNICA);
        pregunta.setOrden(orden);
        return pregunta;
    }

    private CursoOpcion opcion(String id, CursoPregunta pregunta, boolean correcta) {
        CursoOpcion opcion = new CursoOpcion();
        opcion.setId(id);
        opcion.setPregunta(pregunta);
        opcion.setTexto("Opción " + id);
        opcion.setCorrecta(correcta);
        return opcion;
    }
}
