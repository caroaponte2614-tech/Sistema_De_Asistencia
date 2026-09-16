package com.schoolplus.dto;

import com.schoolplus.model.AsistenciaRegistro;
import com.schoolplus.model.AsistenciaSesion;
import com.schoolplus.model.Usuario;

import java.util.List;

/**
 * La sesión con sus registros y, mientras sigue abierta, los matriculados que todavía no
 * aparecen: son los que el profesor puede marcar a mano.
 *
 * <p>Después de cerrar, {@code sinRegistro} queda vacía — el cierre les creó su registro
 * AUSENTE.</p>
 */
public record DetalleSesion(AsistenciaSesion sesion,
                            List<AsistenciaRegistro> registros,
                            List<Usuario> sinRegistro) {
}
