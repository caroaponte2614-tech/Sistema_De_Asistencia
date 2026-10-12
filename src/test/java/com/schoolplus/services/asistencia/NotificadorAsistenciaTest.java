package com.schoolplus.services.asistencia;

import com.schoolplus.model.AsistenciaDispositivo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.ClaseInscripcionRepository;
import com.schoolplus.services.notificaciones.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quién recibe cada aviso de la vinculación de celulares, y a dónde lo lleva. */
@ExtendWith(MockitoExtension.class)
class NotificadorAsistenciaTest {

    @Mock
    private NotificacionService notificaciones;
    @Mock
    private ClaseInscripcionRepository inscripcionRepository;

    private NotificadorAsistencia notificador;
    private Usuario ana;
    private AsistenciaDispositivo celular;

    @BeforeEach
    void preparar() {
        notificador = new NotificadorAsistencia(notificaciones, inscripcionRepository);

        ana = new Usuario();
        ana.setId("est-ana");
        ana.setNombre("Ana Gómez");

        celular = new AsistenciaDispositivo();
        celular.setEstudiante(ana);
        celular.setDescripcion("iPhone/iPad · Safari");
    }

    @Test
    void laSolicitudLlegaACadaProfesorDelEstudiante() {
        when(inscripcionRepository.profesoresDe("est-ana")).thenReturn(List.of("doc-1", "doc-2"));

        notificador.cambioDeCelularPedido(ana, celular);

        for (String profesor : List.of("doc-1", "doc-2")) {
            verify(notificaciones).crear(eq(profesor), eq(NotificadorAsistencia.TIPO_CELULAR_PEDIDO),
                    anyString(), contains("Ana Gómez"), eq("/docencia/asistencia"));
        }
    }

    @Test
    void sinProfesoresNoSeAvisaANadie() {
        when(inscripcionRepository.profesoresDe("est-ana")).thenReturn(List.of());

        notificador.cambioDeCelularPedido(ana, celular);

        verify(notificaciones, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    void laDecisionLeLlegaAlEstudiante() {
        notificador.cambioDeCelularAprobado(celular);
        notificador.cambioDeCelularRechazado(celular);
        notificador.celularDesvinculado(celular);

        verify(notificaciones).crear(eq("est-ana"), eq(NotificadorAsistencia.TIPO_CELULAR_APROBADO),
                anyString(), contains("iPhone/iPad · Safari"), eq("/estudiante/asistencia"));
        verify(notificaciones).crear(eq("est-ana"), eq(NotificadorAsistencia.TIPO_CELULAR_RECHAZADO),
                anyString(), anyString(), eq("/estudiante/asistencia"));
        verify(notificaciones).crear(eq("est-ana"), eq(NotificadorAsistencia.TIPO_CELULAR_REVOCADO),
                anyString(), anyString(), eq("/estudiante/asistencia"));
    }
}
