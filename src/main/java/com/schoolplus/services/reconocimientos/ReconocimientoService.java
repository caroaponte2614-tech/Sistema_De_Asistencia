package com.schoolplus.services.reconocimientos;

import com.schoolplus.model.Capacidades;
import com.schoolplus.model.RegistroReconocimiento;
import com.schoolplus.model.TipoReconocimiento;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.RegistroReconocimientoRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.documentos.ArchivoDescargado;
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
import java.time.LocalDate;
import java.util.List;

/**
 * Reconocimientos y llamados de atención (§ reconocimientos): registro formal, distinto
 * del tag "Reconocimiento" del muro (ver {@code TipoPublicacion.RECONOCIMIENTO}, que es
 * solo una etiqueta de post social sin historial).
 *
 * <p>Sin aprobación: coordinador (sobre su equipo, ámbito AREA) o Talento Humano (ámbito
 * TODOS) registran directamente. El colaborador únicamente lee lo suyo.</p>
 */
@Service
public class ReconocimientoService {

    private static final Logger log = LoggerFactory.getLogger(ReconocimientoService.class);

    private final RegistroReconocimientoRepository registroRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoAlmacenamiento almacenamiento;
    private final CapacidadService capacidades;

    public ReconocimientoService(RegistroReconocimientoRepository registroRepository,
                                 UsuarioRepository usuarioRepository,
                                 DocumentoAlmacenamiento almacenamiento,
                                 CapacidadService capacidades) {
        this.registroRepository = registroRepository;
        this.usuarioRepository = usuarioRepository;
        this.almacenamiento = almacenamiento;
        this.capacidades = capacidades;
    }

    @Transactional
    public RegistroReconocimiento registrar(Usuario actor, String colaboradorId, TipoReconocimiento tipo,
                                            String categoria, String titulo, String descripcion, LocalDate fecha,
                                            MultipartFile soporte) {
        if (!capacidades.puedeSobre(Capacidades.RECONOCIMIENTO_CREAR, colaboradorId)) {
            throw new IllegalArgumentException("Esa persona no está a tu cargo.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Escribe un título.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("Escribe una descripción.");
        }
        if (fecha == null || fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede ser futura.");
        }
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Ese colaborador no existe."));

        RegistroReconocimiento registro = new RegistroReconocimiento();
        registro.setId(UuidV7.nuevoId());
        registro.setColaborador(colaborador);
        registro.setTipo(tipo);
        registro.setCategoria(Textos.vacioANulo(categoria));
        registro.setTitulo(titulo);
        registro.setDescripcion(descripcion);
        registro.setFecha(fecha);
        registro.setRegistradoPor(actor);

        if (soporte != null && !soporte.isEmpty()) {
            ArchivoValidado validado = PoliticaArchivos.validar(soporte, TipoArchivo.SOPORTE);
            try {
                almacenamiento.subir(validado.clave(), soporte.getInputStream(), soporte.getSize(), validado.mime());
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer el archivo de soporte.", e);
            }
            registro.setArchivoSoporteClave(validado.clave());
        }

        registroRepository.save(registro);
        log.info("{} registrado sobre {} por {}", tipo, colaborador.getEmail(), actor.getEmail());
        return registro;
    }

    @Transactional(readOnly = true)
    public List<RegistroReconocimiento> historialDe(Usuario actor, String colaboradorId) {
        // Verse a uno mismo no pasa por el chequeo de ámbito — mismo motivo que
        // PerfilColaboradorService.obtener: el ámbito AREA de un COORDINADOR nunca lo
        // incluye a él mismo, por construcción.
        boolean esUnoMismo = actor.getId().equals(colaboradorId);
        if (!esUnoMismo && !capacidades.puedeSobre(Capacidades.RECONOCIMIENTO_VER, colaboradorId)) {
            throw new IllegalArgumentException("No puedes ver los registros de esa persona.");
        }
        return registroRepository.historialDe(colaboradorId);
    }

    @Transactional(readOnly = true)
    public List<RegistroReconocimiento> misRegistros(Usuario colaborador) {
        return registroRepository.historialDe(colaborador.getId());
    }

    @Transactional(readOnly = true)
    public ArchivoDescargado descargarSoporte(Usuario actor, String registroId) {
        RegistroReconocimiento registro = registroRepository.buscarCompleto(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Ese registro no existe."));
        if (!capacidades.puedeSobre(Capacidades.RECONOCIMIENTO_VER, registro.getColaborador().getId())) {
            throw new IllegalArgumentException("No puedes ver este registro.");
        }
        if (registro.getArchivoSoporteClave() == null) {
            throw new IllegalArgumentException("Ese registro no tiene un archivo adjunto.");
        }
        return new ArchivoDescargado(
                almacenamiento.descargar(registro.getArchivoSoporteClave()),
                "soporte-" + registro.getId(), null, 0);
    }
}
