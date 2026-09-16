package com.schoolplus.services.usuarios;

import java.util.List;

/** Uno o más campos del alta/edición no pasan validación de formato o de unicidad. */
public class DatosUsuarioInvalidosException extends RuntimeException {

    private final List<String> errores;

    public DatosUsuarioInvalidosException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
