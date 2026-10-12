package com.schoolplus.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Formulario de auto-registro.
 *
 * <p>Origen: un proyecto de referencia (dto/RegisterRequest.java).</p>
 */
@Getter
@Setter
public class RegistroForm {

    private String nombre;
    private String email;
    private String cedula;
    private String telefono;
    private String password;
    private String confirmPassword;

    /** Deja el formulario listo para repintarse tras un error, sin las contraseñas. */
    public void limpiarPasswords() {
        this.password = null;
        this.confirmPassword = null;
    }
}
