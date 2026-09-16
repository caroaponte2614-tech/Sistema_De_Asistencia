package com.schoolplus.services.usuarios;

import java.time.LocalDate;

/**
 * Los campos que RH captura al dar de alta o editar un colaborador desde el panel de
 * administración. No incluye contraseña ni rol: la contraseña la fija la propia persona
 * por el flujo de "recuperar contraseña" que ya existe (ver {@link UsuarioAdminService}),
 * y el rol se asigna aparte con {@code UsuarioAdminService.asignarRoles} — RH no debería
 * poder subir un permiso de un colaborador al vuelo, en el mismo formulario donde
 * corrige un teléfono.
 */
public record DatosUsuario(
        String nombre,
        String email,
        String cedula,
        String telefono,
        String sedeId,
        String cargoId,
        String areaId,
        LocalDate fechaIngreso,
        LocalDate fechaNacimiento) {
}
