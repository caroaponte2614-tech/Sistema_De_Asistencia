package com.schoolplus.services.area;

import com.schoolplus.model.Area;
import com.schoolplus.model.EstadoUsuario;
import com.schoolplus.model.Usuario;
import com.schoolplus.repository.AreaRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.util.Textos;
import com.schoolplus.util.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "Áreas y líderes": la pantalla que le faltaba a {@link Area} desde la V1 — el campo
 * {@code coordinador_id} ("líder") ya existía en el esquema, pero ninguna pantalla lo
 * exponía, y tampoco había forma de dar de alta un área nueva salvo por SQL directo.
 *
 * <p>Vive aparte de {@code UsuarioAdminService} a propósito: es exclusiva del tablero de
 * área (D-030), no del módulo general de usuarios.</p>
 */
@Service
public class AreaAdminService {

    private final AreaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;

    public AreaAdminService(AreaRepository areaRepository, UsuarioRepository usuarioRepository) {
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Area> listarTodas() {
        return areaRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<Usuario> candidatosALider() {
        return usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.ACTIVO);
    }

    @Transactional
    public Area crear(String nombre) {
        String limpio = Textos.vacioANulo(nombre);
        if (limpio == null) {
            throw new IllegalArgumentException("El nombre del área es obligatorio.");
        }
        if (areaRepository.existsByNombre(limpio)) {
            throw new IllegalArgumentException("Ya existe un área con ese nombre.");
        }
        Area area = new Area();
        area.setId(UuidV7.nuevoId());
        area.setNombre(limpio);
        area.setActivo(true);
        return areaRepository.save(area);
    }

    /** {@code nuevoLiderId} vacío o nulo quita el líder actual. */
    @Transactional
    public void asignarLider(String areaId, String nuevoLiderId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa área no existe."));
        String id = Textos.vacioANulo(nuevoLiderId);
        if (id == null) {
            area.setCoordinadorId(null);
        } else {
            Usuario lider = usuarioRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Ese usuario no existe."));
            area.setCoordinadorId(lider.getId());
        }
        areaRepository.save(area);
    }

    @Transactional
    public void cambiarEstado(String areaId, boolean activo) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Esa área no existe."));
        area.setActivo(activo);
        areaRepository.save(area);
    }
}
