package com.schoolplus.services.asistencia;

import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Decide, en el apretón de manos, si quien abre el socket puede seguir esa sesión.
 *
 * <p>La comprobación va aquí y no dentro del handler por un motivo concreto: el permiso se
 * resuelve con {@code CapacidadService}, que lee el usuario autenticado del contexto de
 * seguridad, y ese contexto existe en el hilo de la petición HTTP del apretón de manos —no
 * en los hilos en los que después llegan los mensajes del socket.</p>
 *
 * <p>Sin permiso se responde 403 y el socket nunca llega a abrirse.</p>
 */
@Component
public class AsistenciaHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaHandshakeInterceptor.class);

    private final AsistenciaService asistenciaService;
    private final UsuarioRepository usuarioRepository;

    public AsistenciaHandshakeInterceptor(AsistenciaService asistenciaService,
                                          UsuarioRepository usuarioRepository) {
        this.asistenciaService = asistenciaService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler handler, Map<String, Object> atributos) {
        String sesionId = UriComponentsBuilder.fromUri(peticion.getURI())
                .build().getQueryParams().getFirst("sesion");
        if (sesionId == null || sesionId.isBlank() || peticion.getPrincipal() == null) {
            respuesta.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        Optional<Usuario> actor = usuarioRepository.findByEmail(peticion.getPrincipal().getName());
        if (actor.isEmpty() || !asistenciaService.puedeSeguirEnVivo(actor.get(), sesionId)) {
            log.warn("Intento de seguir en vivo la sesión {} sin permiso", sesionId);
            respuesta.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        atributos.put(AsistenciaEnVivoHandler.ATRIBUTO_SESION, sesionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                               WebSocketHandler handler, Exception excepcion) {
        // Nada que hacer: el socket ya quedó registrado en el handler.
    }
}
