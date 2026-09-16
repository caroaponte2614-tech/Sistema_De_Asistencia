package com.schoolplus.configuration.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resuelve directamente al {@link com.schoolplus.model.Usuario} autenticado como
 * parámetro del método del controlador, en vez de recibir {@code Authentication} y
 * buscarlo a mano.
 *
 * <p>Antes de esto, cada controlador repetía el mismo
 * {@code usuarioRepository.findByEmail(autenticacion.getName()).orElseThrow(...)} — un
 * método privado {@code actor(Authentication)} copiado en ocho archivos. Ver
 * {@link ActorArgumentResolver}.</p>
 *
 * <pre>{@code
 * @PostMapping("/algo")
 * public String algo(@Actor Usuario actor, ...) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Actor {
}
