package com.schoolplus.configuration.web;

import com.schoolplus.model.Usuario;
import com.schoolplus.repository.UsuarioRepository;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resuelve un parámetro {@code @Actor Usuario} buscando al usuario autenticado por su
 * email. Registrado en {@link WebMvcActorConfig}.
 *
 * <p>El email sale del {@code SecurityContextHolder} y no del {@code Authentication} que
 * Spring podría inyectar como argumento aparte: a esta altura del ciclo de la petición ya
 * está poblado por el filtro de seguridad, y evita declarar dos parámetros
 * (uno para autenticación, otro para el actor) cuando el controlador solo necesita el
 * segundo.</p>
 */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final UsuarioRepository usuarioRepository;

    public ActorArgumentResolver(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Actor.class)
                && Usuario.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String email = autenticacion == null ? null : autenticacion.getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(String.valueOf(email)));
    }
}
