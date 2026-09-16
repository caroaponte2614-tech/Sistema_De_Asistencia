package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Formulario de auto-registro.
 *
 * <p>Origen: un proyecto de referencia (dto/RegisterRequest.java). Cambios: {@code ciudad} y
 * {@code punto} eran texto libre validado contra un {@code Set.of()}; aquí son el id de una
 * sede y el id de un cargo del catálogo en base de datos.</p>
 */
@Getter
@Setter
public class RegistroForm {

    private String nombre;
    private String email;
    private String cedula;
    private String telefono;
    private String sedeId;
    private String cargoId;
    private String password;
    private String confirmPassword;

    /** Deja el formulario listo para repintarse tras un error, sin las contraseñas. */
    public void limpiarPasswords() {
        this.password = null;
        this.confirmPassword = null;
    }
}
