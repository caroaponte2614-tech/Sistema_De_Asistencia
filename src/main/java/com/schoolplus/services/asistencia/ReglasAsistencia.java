package com.schoolplus.services.asistencia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Los números del módulo de asistencia, todos configurables por entorno.
 *
 * <p>Están juntos en una clase y no repartidos en {@code @Value} por los servicios para que
 * se puedan leer de un vistazo y para que los tests los fijen sin levantar Spring.</p>
 */
@Component
public class ReglasAsistencia {

    private final int rotacionSegundos;
    private final int ventanasDeGracia;
    private final int segundosParaConfirmar;
    private final int toleranciaPorDefectoMinutos;
    private final int duracionMaximaHoras;
    private final boolean geoHabilitada;
    private final double geoLatitud;
    private final double geoLongitud;
    private final int geoRadioMetros;

    public ReglasAsistencia(
            @Value("${app.asistencia.rotacion-segundos:15}") int rotacionSegundos,
            @Value("${app.asistencia.ventanas-de-gracia:1}") int ventanasDeGracia,
            @Value("${app.asistencia.segundos-para-confirmar:120}") int segundosParaConfirmar,
            @Value("${app.asistencia.tolerancia-minutos:10}") int toleranciaPorDefectoMinutos,
            @Value("${app.asistencia.duracion-maxima-horas:4}") int duracionMaximaHoras,
            @Value("${app.asistencia.geo.habilitada:false}") boolean geoHabilitada,
            @Value("${app.asistencia.geo.latitud:0}") double geoLatitud,
            @Value("${app.asistencia.geo.longitud:0}") double geoLongitud,
            @Value("${app.asistencia.geo.radio-metros:300}") int geoRadioMetros) {
        this.rotacionSegundos = Math.max(5, rotacionSegundos);
        this.ventanasDeGracia = Math.max(0, ventanasDeGracia);
        this.segundosParaConfirmar = Math.max(30, segundosParaConfirmar);
        this.toleranciaPorDefectoMinutos = toleranciaPorDefectoMinutos;
        this.duracionMaximaHoras = Math.max(1, duracionMaximaHoras);
        this.geoHabilitada = geoHabilitada;
        this.geoLatitud = geoLatitud;
        this.geoLongitud = geoLongitud;
        this.geoRadioMetros = geoRadioMetros;
    }

    /** Cada cuántos segundos cambian el QR y el código. */
    public int rotacionSegundos() {
        return rotacionSegundos;
    }

    /**
     * Cuántas ventanas anteriores se siguen aceptando. Con 1 y rotación de 15 s, un QR vale
     * entre 15 y 30 segundos: lo suficiente para escanear con mala luz, muy poco para
     * mandarle la foto a alguien que no está en el salón.
     */
    public int ventanasDeGracia() {
        return ventanasDeGracia;
    }

    /**
     * Cuánto tiempo vale un escaneo ya validado mientras el estudiante inicia sesión o
     * concede el permiso de ubicación. El QR tuvo que estar vigente en el momento del
     * escaneo; esto solo cubre los segundos siguientes.
     */
    public int segundosParaConfirmar() {
        return segundosParaConfirmar;
    }

    public int toleranciaPorDefectoMinutos() {
        return toleranciaPorDefectoMinutos;
    }

    /** A las cuántas horas el sistema cierra solo una sesión que quedó abierta. */
    public int duracionMaximaHoras() {
        return duracionMaximaHoras;
    }

    public boolean geoHabilitada() {
        return geoHabilitada;
    }

    public int geoRadioMetros() {
        return geoRadioMetros;
    }

    /**
     * ¿El punto está dentro del radio del colegio? Fórmula del semiverseno sobre una
     * esfera de 6 371 km: para distancias de metros, el error frente a un elipsoide es
     * irrelevante.
     */
    public boolean dentroDelRadio(BigDecimal latitud, BigDecimal longitud) {
        if (!geoHabilitada) {
            return true;
        }
        if (latitud == null || longitud == null) {
            return false;
        }
        double radioTierra = 6_371_000;
        double latitudRad = Math.toRadians(latitud.doubleValue());
        double latitudColegioRad = Math.toRadians(geoLatitud);
        double deltaLatitud = Math.toRadians(latitud.doubleValue() - geoLatitud);
        double deltaLongitud = Math.toRadians(longitud.doubleValue() - geoLongitud);

        double a = Math.sin(deltaLatitud / 2) * Math.sin(deltaLatitud / 2)
                + Math.cos(latitudColegioRad) * Math.cos(latitudRad)
                * Math.sin(deltaLongitud / 2) * Math.sin(deltaLongitud / 2);
        double distancia = 2 * radioTierra * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return distancia <= geoRadioMetros;
    }
}
