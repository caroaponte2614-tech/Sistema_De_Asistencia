package com.schoolplus.dto;

/**
 * Datos del usuario que pinta el panel.
 *
 * <p>Es un DTO y no la entidad para que el hash de la contraseña nunca entre al modelo de
 * la vista. {@code roles} llega ya como texto ("Docente, Rectoría").</p>
 */
public record UsuarioResumen(String nombre, String email, String roles) {
}
