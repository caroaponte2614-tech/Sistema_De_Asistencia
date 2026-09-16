package com.schoolplus.model;

/**
 * Códigos de la tabla {@code capacidad}, con nombre.
 *
 * <p>La matriz vive en la base de datos —eso no cambia—, pero los códigos se escriben
 * en {@code @PreAuthorize} y en las plantillas, y ahí una constante evita que un dedazo
 * se convierta en un permiso que nunca concede nada y en una pantalla en blanco sin
 * explicación. {@code CapacidadesTest} comprueba que esta lista y la migración V1 no se
 * separen.</p>
 */
public final class Capacidades {

    public static final String DOCUMENTO_RESTRINGIDO_SUBIR = "DOCUMENTO_RESTRINGIDO_SUBIR";
    public static final String DOCUMENTO_RESTRINGIDO_VER = "DOCUMENTO_RESTRINGIDO_VER";
    public static final String DOCUMENTO_PUBLICO_SUBIR = "DOCUMENTO_PUBLICO_SUBIR";
    public static final String TABLERO_USAR = "TABLERO_USAR";
    public static final String TABLERO_VER = "TABLERO_VER";
    public static final String SOLICITUD_CREAR = "SOLICITUD_CREAR";
    public static final String SOLICITUD_APROBAR_PRIMERA = "SOLICITUD_APROBAR_PRIMERA";
    public static final String SOLICITUD_APROBAR_SEGUNDA = "SOLICITUD_APROBAR_SEGUNDA";
    public static final String HORAS_EXTRA_REGISTRAR = "HORAS_EXTRA_REGISTRAR";
    public static final String HORAS_EXTRA_APROBAR = "HORAS_EXTRA_APROBAR";
    public static final String HORAS_EXTRA_VER = "HORAS_EXTRA_VER";
    public static final String PORTAL_PUBLICAR = "PORTAL_PUBLICAR";
    public static final String PORTAL_ENCUESTA_CREAR = "PORTAL_ENCUESTA_CREAR";
    public static final String PORTAL_COMENTAR = "PORTAL_COMENTAR";
    public static final String PORTAL_MODERAR = "PORTAL_MODERAR";
    public static final String REPORTE_GENERAR = "REPORTE_GENERAR";
    public static final String USUARIO_ADMINISTRAR = "USUARIO_ADMINISTRAR";
    public static final String ORGANIGRAMA_ADMINISTRAR = "ORGANIGRAMA_ADMINISTRAR";
    public static final String ORGANIGRAMA_VER = "ORGANIGRAMA_VER";
    public static final String CAPACITACION_VER = "CAPACITACION_VER";
    public static final String CAPACITACION_ADMINISTRAR = "CAPACITACION_ADMINISTRAR";
    public static final String DOCUMENTO_INSTITUCIONAL_VER = "DOCUMENTO_INSTITUCIONAL_VER";
    public static final String DOCUMENTO_INSTITUCIONAL_ADMINISTRAR = "DOCUMENTO_INSTITUCIONAL_ADMINISTRAR";
    public static final String RECONOCIMIENTO_CREAR = "RECONOCIMIENTO_CREAR";
    public static final String RECONOCIMIENTO_VER = "RECONOCIMIENTO_VER";
    public static final String PERFIL_COLABORADOR_ADMINISTRAR = "PERFIL_COLABORADOR_ADMINISTRAR";
    public static final String PERFIL_COLABORADOR_VER = "PERFIL_COLABORADOR_VER";
    public static final String CERTIFICADO_LABORAL_SOLICITAR = "CERTIFICADO_LABORAL_SOLICITAR";
    public static final String CERTIFICADO_LABORAL_GENERAR_TERCEROS = "CERTIFICADO_LABORAL_GENERAR_TERCEROS";

    private Capacidades() {
    }
}
