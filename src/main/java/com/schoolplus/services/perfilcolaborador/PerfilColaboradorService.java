package com.schoolplus.services.perfilcolaborador;

import com.schoolplus.dto.HijoEntrada;
import com.schoolplus.dto.PerfilDetalle;
import com.schoolplus.model.Capacidades;
import com.schoolplus.model.PerfilColaborador;
import com.schoolplus.model.PerfilEvaluacion;
import com.schoolplus.model.PerfilHijo;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.PerfilColaboradorRepository;
import com.schoolplus.repository.PerfilEvaluacionRepository;
import com.schoolplus.repository.PerfilHijoRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.permisos.CapacidadService;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Identificación del colaborador (§ perfil): una ficha estructurada que llena el
 * supervisor —contacto de emergencia, hijos, evaluaciones de desempeño— y que el
 * propio colaborador solo lee.
 *
 * <p>{@code perfil_evaluacion} es de solo-agregar: una evaluación pasada no se edita ni
 * se borra. {@code perfil_hijo} describe el estado actual y se reemplaza completo en
 * cada guardado, como el quiz de {@code CursoService}.</p>
 */
@Service
public class PerfilColaboradorService {

    private static final Logger log = LoggerFactory.getLogger(PerfilColaboradorService.class);

    private final PerfilColaboradorRepository perfilRepository;
    private final PerfilHijoRepository hijoRepository;
    private final PerfilEvaluacionRepository evaluacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadService capacidades;

    public PerfilColaboradorService(PerfilColaboradorRepository perfilRepository,
                                    PerfilHijoRepository hijoRepository,
                                    PerfilEvaluacionRepository evaluacionRepository,
                                    UsuarioRepository usuarioRepository,
                                    CapacidadService capacidades) {
        this.perfilRepository = perfilRepository;
        this.hijoRepository = hijoRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.capacidades = capacidades;
    }

    @Transactional(readOnly = true)
    public PerfilDetalle obtener(Usuario actor, String colaboradorId) {
        // Verse a uno mismo no necesita el chequeo de ámbito: puedeSobre resuelve un solo
        // ámbito por capacidad (el mayor entre los roles del actor — ver CapacidadService),
        // y para un COORDINADOR ese ámbito es AREA ("mi equipo"), que por construcción
        // nunca incluye al propio actor (nadie es su propio subordinado). El @PreAuthorize
        // del controlador ya exige PERFIL_COLABORADOR_VER en algún ámbito para llegar acá,
        // así que ver la propia ficha no exige nada más.
        boolean esUnoMismo = actor.getId().equals(colaboradorId);
        if (!esUnoMismo && !capacidades.puedeSobre(Capacidades.PERFIL_COLABORADOR_VER, colaboradorId)) {
            throw new IllegalArgumentException("No puedes ver la ficha de esa persona.");
        }
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));
        PerfilColaborador perfil = perfilRepository.findById(colaboradorId).orElse(null);
        List<PerfilHijo> hijos = hijoRepository.findByUsuarioIdOrderByOrdenAsc(colaboradorId);
        List<PerfilEvaluacion> evaluaciones = evaluacionRepository.findByUsuarioIdOrderByFechaDesc(colaboradorId);
        return new PerfilDetalle(colaborador, perfil, hijos, evaluaciones);
    }

    @Transactional
    public void guardarDatosBasicos(Usuario actor, String colaboradorId, String contactoNombre,
                                    String contactoParentesco, String contactoTelefono) {
        exigirPuedeAdministrar(colaboradorId);
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));

        PerfilColaborador perfil = perfilRepository.findById(colaboradorId).orElseGet(() -> {
            PerfilColaborador nuevo = new PerfilColaborador();
            nuevo.setUsuarioId(colaboradorId);
            return nuevo;
        });
        perfil.setContactoEmergenciaNombre(Textos.vacioANulo(contactoNombre));
        perfil.setContactoEmergenciaParentesco(Textos.vacioANulo(contactoParentesco));
        perfil.setContactoEmergenciaTelefono(Textos.vacioANulo(contactoTelefono));
        perfil.setActualizadoPor(actor);
        perfilRepository.save(perfil);
        log.info("Datos básicos de {} actualizados por {}", colaborador.getEmail(), actor.getEmail());
    }

    @Transactional
    public void reemplazarHijos(Usuario actor, String colaboradorId, List<HijoEntrada> hijos) {
        exigirPuedeAdministrar(colaboradorId);
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));

        hijoRepository.deleteByUsuarioId(colaboradorId);
        int orden = 0;
        for (HijoEntrada entrada : hijos) {
            if (entrada.nombre() == null || entrada.nombre().isBlank()) {
                throw new IllegalArgumentException("Cada hijo necesita un nombre.");
            }
            if (entrada.fechaNacimiento() == null) {
                throw new IllegalArgumentException("Cada hijo necesita una fecha de nacimiento.");
            }
            PerfilHijo hijo = new PerfilHijo();
            hijo.setId(UuidV7.nuevoId());
            hijo.setUsuario(colaborador);
            hijo.setNombre(entrada.nombre());
            hijo.setFechaNacimiento(entrada.fechaNacimiento());
            hijo.setOrden(orden++);
            hijoRepository.save(hijo);
        }
        log.info("Hijos de {} actualizados por {} ({} en total)", colaborador.getEmail(), actor.getEmail(), hijos.size());
    }

    @Transactional
    public PerfilEvaluacion agregarEvaluacion(Usuario actor, String colaboradorId, LocalDate fecha,
                                              String calificacion, String comentario) {
        exigirPuedeAdministrar(colaboradorId);
        if (fecha == null || fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede ser futura.");
        }
        if (calificacion == null || calificacion.isBlank()) {
            throw new IllegalArgumentException("Escribe una calificación.");
        }
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));

        PerfilEvaluacion evaluacion = new PerfilEvaluacion();
        evaluacion.setId(UuidV7.nuevoId());
        evaluacion.setUsuario(colaborador);
        evaluacion.setFecha(fecha);
        evaluacion.setCalificacion(calificacion);
        evaluacion.setComentario(Textos.vacioANulo(comentario));
        evaluacion.setEvaluadoPor(actor);
        evaluacionRepository.save(evaluacion);
        log.info("Evaluación agregada para {} por {}", colaborador.getEmail(), actor.getEmail());
        return evaluacion;
    }

    private void exigirPuedeAdministrar(String colaboradorId) {
        if (!capacidades.puedeSobre(Capacidades.PERFIL_COLABORADOR_ADMINISTRAR, colaboradorId)) {
            throw new IllegalArgumentException("No puedes editar la ficha de esa persona.");
        }
    }
}
