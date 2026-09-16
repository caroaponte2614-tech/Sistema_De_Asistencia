package com.schoolplus.services.asistencia;

/**
 * Por qué no se pudo registrar la asistencia, con el texto que ve el estudiante en el
 * celular.
 *
 * <p>Los mensajes son parte del módulo y no del controlador a propósito: un estudiante
 * frente a una pantalla roja necesita saber qué hacer ahora ("vuelve a escanear", "avisa a
 * tu profesor"), no un código de error.</p>
 */
public enum MotivoRechazo {

    QR_VENCIDO("El código QR venció",
            "Ese QR ya cambió. Vuelve a escanear el que está en la pantalla ahora mismo."),

    QR_INVALIDO("QR no válido",
            "Ese código no corresponde a una sesión de asistencia de School_Plus."),

    CODIGO_INVALIDO("Código incorrecto",
            "Revisa los 6 dígitos de la pantalla. Cambian cada pocos segundos, así que escríbelos rápido."),

    SESION_CERRADA("La sesión ya se cerró",
            "Tu profesor cerró el registro de esta clase. Háblale para que revise tu asistencia."),

    NO_INSCRITO("No estás en esta clase",
            "No apareces matriculado en la clase de esta sesión. Avisa en coordinación si es un error."),

    YA_REGISTRADO("Ya estabas registrado",
            "Tu asistencia de esta clase ya había quedado registrada."),

    DISPOSITIVO_NO_IDENTIFICADO("No pudimos reconocer este celular",
            "Tu navegador está bloqueando las cookies del sitio y sin ellas no podemos vincular tu celular. "
                    + "Habilítalas e inténtalo otra vez."),

    DISPOSITIVO_NO_AUTORIZADO("Este no es tu celular vinculado",
            "Registramos la solicitud para que un profesor autorice este celular. "
                    + "Mientras tanto, usa el de siempre o pide que te marquen a mano."),

    DISPOSITIVO_DE_OTRO_ESTUDIANTE("Este celular es de otra cuenta",
            "Este teléfono ya está vinculado a otro estudiante. Cada persona registra desde el suyo."),

    DISPOSITIVO_YA_USADO("Este celular ya registró a alguien",
            "Con este teléfono ya se registró otro estudiante en esta misma sesión."),

    UBICACION_REQUERIDA("Falta tu ubicación",
            "Para registrar asistencia hay que confirmar que estás en el colegio. "
                    + "Permite el acceso a la ubicación y vuelve a intentarlo."),

    FUERA_DEL_COLEGIO("Estás fuera del colegio",
            "Tu ubicación está lejos del colegio. El registro solo funciona dentro del campus."),

    ESCANEO_EXPIRADO("Se acabó el tiempo",
            "Pasó demasiado tiempo desde que escaneaste. Vuelve a escanear el QR de la pantalla.");

    private final String titulo;
    private final String mensaje;

    MotivoRechazo(String titulo, String mensaje) {
        this.titulo = titulo;
        this.mensaje = mensaje;
    }

    public String titulo() {
        return titulo;
    }

    public String mensaje() {
        return mensaje;
    }
}
