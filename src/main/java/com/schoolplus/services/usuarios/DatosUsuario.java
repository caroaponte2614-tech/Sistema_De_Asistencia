package com.schoolplus.services.usuarios;

import java.time.LocalDate;

/**
 * Los campos que se capturan al dar de alta o editar un usuario desde el panel de
 * administración. No incluye contraseña ni rol: la contraseña la fija la propia persona
 * por el flujo de "recuperar contraseña" que ya existe (ver {@link UsuarioAdminService}),
 * y el rol se asigna aparte con {@code UsuarioAdminService.asignarRoles} — no debería
 * poder subirse un permiso al vuelo, en el mismo formulario donde se corrige un teléfono.
 */
public record DatosUsuario(
        String nombre,
        String email,
        String cedula,
        String telefono,
        LocalDate fechaNacimiento) {
}
