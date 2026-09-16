package com.schoolplus.dto;

/**
 * Datos del usuario que pinta el panel.
 *
 * <p>Es un DTO y no la entidad porque {@code open-in-view} está apagado: la sede y el cargo
 * se resuelven dentro de la transacción y llegan a la plantilla ya como texto. Además, de
 * este modo el hash de la contraseña nunca entra al modelo de la vista.</p>
 */
public record UsuarioResumen(String nombre, String email, String sede, String cargo) {
}
