package com.schoolplus.services.auth;

import com.schoolplus.dto.RegistroForm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La política de contraseñas es lo que separa a School_Plus del original: un proyecto de referencia
 * aceptaba seis caracteres cualesquiera (hallazgo #5 de docs/DECISIONES.md).
 */
class ValidationServiceTest {

    private final ValidationService validation = new ValidationService(10);

    @Test
    void unRegistroCompletoYCorrectoNoDaErrores() {
        assertThat(validation.validarRegistro(formValido())).isEmpty();
    }

    @Test
    void rechazaLaContrasenaCorta() {
        RegistroForm form = formValido();
        form.setPassword("clave12");
        form.setConfirmPassword("clave12");

        assertThat(validation.validarRegistro(form))
                .anyMatch(error -> error.contains("al menos 10 caracteres"));
    }

    @Test
    void exigeMezclarLetrasYNumeros() {
        List<String> soloLetras = validation.validarPassword(
                "contrasenalarga", "contrasenalarga", "Ana Gómez", "ana@schoolplus.example");
        List<String> soloNumeros = validation.validarPassword(
                "9081726354", "9081726354", "Ana Gómez", "ana@schoolplus.example");

        assertThat(soloLetras).anyMatch(error -> error.contains("letras y números"));
        assertThat(soloNumeros).anyMatch(error -> error.contains("letras y números"));
    }

    @Test
    void rechazaLasClavesComunes() {
        assertThat(validation.validarPassword("password1", "password1", "Ana", "ana@schoolplus.example"))
                .anyMatch(error -> error.contains("demasiado común"));
    }

    @Test
    void rechazaLaContrasenaQueContieneElNombreOElCorreo() {
        assertThat(validation.validarPassword(
                "ChristianX2026", "ChristianX2026", "Christian Ruiz", "cruiz@schoolplus.example"))
                .anyMatch(error -> error.contains("tu nombre"));

        assertThat(validation.validarPassword(
                "cruiz2026abc", "cruiz2026abc", "Christian Ruiz", "cruiz@schoolplus.example"))
                .anyMatch(error -> error.contains("tu correo"));
    }

    @Test
    void exigeQueLasDosContrasenasCoincidan() {
        assertThat(validation.validarPassword(
                "Alameda2026", "Alameda2027", "Ana Gómez", "ana@schoolplus.example"))
                .anyMatch(error -> error.contains("no coinciden"));
    }

    @Test
    void validaCedulaTelefonoYNombreConLosRegexHeredados() {
        RegistroForm form = formValido();
        form.setCedula("12ab");
        form.setTelefono("no-es-un-numero");
        form.setNombre("A");

        List<String> errores = validation.validarRegistro(form);

        assertThat(errores)
                .anyMatch(error -> error.contains("cédula"))
                .anyMatch(error -> error.contains("teléfono"))
                .anyMatch(error -> error.contains("3 y 110 letras"));
    }

    @Test
    void exigeSedeYCargo() {
        RegistroForm form = formValido();
        form.setSedeId(null);
        form.setCargoId("  ");

        assertThat(validation.validarRegistro(form))
                .anyMatch(error -> error.contains("sede"))
                .anyMatch(error -> error.contains("cargo"));
    }

    private RegistroForm formValido() {
        RegistroForm form = new RegistroForm();
        form.setNombre("Ana Gómez");
        form.setEmail("ana.gomez@schoolplus.example");
        form.setCedula("1020304050");
        form.setTelefono("3001234567");
        form.setSedeId("11111111-1111-1111-1111-111111111111");
        form.setCargoId("22222222-2222-2222-2222-222222222222");
        form.setPassword("Alameda2026");
        form.setConfirmPassword("Alameda2026");
        return form;
    }
}
