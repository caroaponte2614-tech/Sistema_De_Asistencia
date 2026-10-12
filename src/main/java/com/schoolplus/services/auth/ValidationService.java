package com.schoolplus.services.auth;

import com.schoolplus.dto.RegistroForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación de los datos de registro y de la política de contraseñas.
 *
 * <p>Origen: un proyecto de referencia (services/auth/ValidationService.java). Se conservan sus
 * expresiones regulares, que son buenas. Un cambio respecto al original
 * (docs/DECISIONES.md):</p>
 * <ul>
 *   <li>La contraseña mínima sube de <strong>6 a 10</strong> caracteres, y se exige mezcla
 *       de letras y dígitos más una lista de claves comunes.</li>
 * </ul>
 */
@Service
public class ValidationService {

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L} ]{3,110}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CEDULA = Pattern.compile("^\\d{6,12}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\d{1,15}$");
    private static final Pattern TIENE_LETRA = Pattern.compile(".*\\p{L}.*");
    private static final Pattern TIENE_DIGITO = Pattern.compile(".*\\d.*");

    /**
     * Claves que un diccionario prueba en los primeros segundos. La lista es corta a
     * propósito: no pretende ser exhaustiva, sino cortar lo evidente sin traer una
     * dependencia con un diccionario de millones de entradas.
     */
    private static final Set<String> PASSWORDS_COMUNES = Set.of(
            "contrasena", "contraseña", "password", "password1", "passw0rd",
            "12345678", "123456789", "1234567890", "qwertyuiop", "qwerty123",
            "administrador", "schoolplus", "schoolplus1", "school_plus",
            "bienvenido", "bienvenido1", "iloveyou1");

    private final int longitudMinima;

    public ValidationService(@Value("${app.security.password.longitud-minima:10}") int longitudMinima) {
        this.longitudMinima = longitudMinima;
    }

    public int getLongitudMinima() {
        return longitudMinima;
    }

    /** Valida el formato de los campos del registro. No consulta la base de datos. */
    public List<String> validarRegistro(RegistroForm form) {
        List<String> errores = new ArrayList<>(
                validarDatosBasicos(form.getNombre(), form.getEmail(), form.getCedula(), form.getTelefono()));

        errores.addAll(validarPassword(form.getPassword(), form.getConfirmPassword(), form.getNombre(), form.getEmail()));

        return errores;
    }

    /**
     * Los cuatro campos de identidad que comparten el auto-registro y el alta manual
     * desde el panel de administración. No consulta la base de datos:
     * unicidad de correo/cédula la valida quien llama, contra el repositorio.
     */
    public List<String> validarDatosBasicos(String nombre, String email, String cedula, String telefono) {
        List<String> errores = new ArrayList<>();

        if (vacio(nombre)) {
            errores.add("El nombre es obligatorio.");
        } else if (!NOMBRE.matcher(nombre.trim()).matches()) {
            errores.add("El nombre debe tener entre 3 y 110 letras.");
        }

        if (vacio(email)) {
            errores.add("El correo es obligatorio.");
        } else if (!EMAIL.matcher(email.trim()).matches()) {
            errores.add("El formato del correo no es válido.");
        }

        if (vacio(cedula)) {
            errores.add("La cédula es obligatoria.");
        } else if (!CEDULA.matcher(cedula.trim()).matches()) {
            errores.add("La cédula debe tener entre 6 y 12 dígitos.");
        }

        if (vacio(telefono)) {
            errores.add("El teléfono es obligatorio.");
        } else if (!TELEFONO.matcher(telefono.trim()).matches()) {
            errores.add("El teléfono debe tener entre 1 y 15 dígitos.");
        }

        return errores;
    }

    /** Política de contraseñas. La usan el registro y el restablecimiento. */
    public List<String> validarPassword(String password, String confirmacion, String nombre, String email) {
        List<String> errores = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errores.add("La contraseña es obligatoria.");
            return errores;
        }

        if (password.length() < longitudMinima) {
            errores.add("La contraseña debe tener al menos " + longitudMinima + " caracteres.");
        }
        if (!TIENE_LETRA.matcher(password).matches() || !TIENE_DIGITO.matcher(password).matches()) {
            errores.add("La contraseña debe combinar letras y números.");
        }
        if (PASSWORDS_COMUNES.contains(password.toLowerCase())) {
            errores.add("Esa contraseña es demasiado común. Elige otra.");
        }
        if (contieneAlgunNombre(password, nombre)) {
            errores.add("La contraseña no puede contener tu nombre.");
        }
        if (contieneDatoPersonal(password, parteLocal(email))) {
            errores.add("La contraseña no puede contener tu correo.");
        }
        if (confirmacion == null || confirmacion.isEmpty()) {
            errores.add("Debes confirmar la contraseña.");
        } else if (!password.equals(confirmacion)) {
            errores.add("Las contraseñas no coinciden.");
        }

        return errores;
    }

    /**
     * Mira nombre y apellidos por separado: "Christian Ruiz" hace inaceptable
     * "Christian2026", que es exactamente la contraseña que la gente elige.
     */
    private boolean contieneAlgunNombre(String password, String nombreCompleto) {
        if (vacio(nombreCompleto)) {
            return false;
        }
        for (String parte : nombreCompleto.trim().split("\\s+")) {
            if (contieneDatoPersonal(password, parte)) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneDatoPersonal(String password, String dato) {
        if (vacio(dato)) {
            return false;
        }
        String limpio = dato.trim().toLowerCase();
        // Fragmentos de dos o tres letras aparecen en cualquier contraseña por azar.
        return limpio.length() >= 4 && password.toLowerCase().contains(limpio);
    }

    private static String parteLocal(String email) {
        if (vacio(email)) {
            return null;
        }
        int arroba = email.indexOf('@');
        return arroba > 0 ? email.substring(0, arroba) : email;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
