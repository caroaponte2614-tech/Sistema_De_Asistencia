package com.schoolplus.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El diagrama del §4.4, comprobado arista por arista.
 *
 * <p>Este test es deliberadamente literal: si alguien añade un estado o una transición que
 * el diagrama no dibuja, aquí revienta. Es la red que protege la parte más delicada del
 * sistema (§5.4).</p>
 */
class EstadoSolicitudTest {

    @Test
    void solamenteExistenLosSeisEstadosDelDiagrama() {
        assertThat(EstadoSolicitud.values()).containsExactly(
                EstadoSolicitud.BORRADOR,
                EstadoSolicitud.PENDIENTE_COORDINADOR,
                EstadoSolicitud.PENDIENTE_SUPERVISOR,
                EstadoSolicitud.APROBADO,
                EstadoSolicitud.RECHAZADO_COORD,
                EstadoSolicitud.RECHAZADO_RRHH);
    }

    @Test
    void elBorradorSaleHaciaCualquieraDeLasDosInstancias() {
        assertThat(EstadoSolicitud.BORRADOR.siguientes()).containsExactlyInAnyOrder(
                EstadoSolicitud.PENDIENTE_COORDINADOR,
                EstadoSolicitud.PENDIENTE_SUPERVISOR);
    }

    @Test
    void elCoordinadorSoloPuedeAprobarODenegar() {
        assertThat(EstadoSolicitud.PENDIENTE_COORDINADOR.siguientes()).containsExactlyInAnyOrder(
                EstadoSolicitud.PENDIENTE_SUPERVISOR,
                EstadoSolicitud.RECHAZADO_COORD);
    }

    @Test
    void talentoHumanoSoloPuedeAprobarODenegar() {
        assertThat(EstadoSolicitud.PENDIENTE_SUPERVISOR.siguientes()).containsExactlyInAnyOrder(
                EstadoSolicitud.APROBADO,
                EstadoSolicitud.RECHAZADO_RRHH);
    }

    @Test
    void siElCoordinadorDeniegaElFlujoTerminaAhi() {
        // La regla dura del §4.4: "nunca llega a RRHH".
        assertThat(EstadoSolicitud.RECHAZADO_COORD.esFinal()).isTrue();
        assertThat(EstadoSolicitud.RECHAZADO_COORD.puedePasarA(EstadoSolicitud.PENDIENTE_SUPERVISOR)).isFalse();
        assertThat(EstadoSolicitud.RECHAZADO_COORD.puedePasarA(EstadoSolicitud.APROBADO)).isFalse();
    }

    @Test
    void losTresEstadosFinalesNoTienenSalida() {
        assertThat(EstadoSolicitud.APROBADO.esFinal()).isTrue();
        assertThat(EstadoSolicitud.RECHAZADO_COORD.esFinal()).isTrue();
        assertThat(EstadoSolicitud.RECHAZADO_RRHH.esFinal()).isTrue();
    }

    @Test
    void unaSolicitudAprobadaNoSePuedeReabrir() {
        for (EstadoSolicitud destino : EstadoSolicitud.values()) {
            assertThat(EstadoSolicitud.APROBADO.puedePasarA(destino))
                    .as("APROBADO no debería poder pasar a %s", destino)
                    .isFalse();
        }
    }

    @Test
    void noSePuedeSaltarLaPrimeraInstanciaDesdeLaBandejaDelCoordinador() {
        // Estando pendiente del coordinador, no hay atajo a APROBADO.
        assertThat(EstadoSolicitud.PENDIENTE_COORDINADOR.puedePasarA(EstadoSolicitud.APROBADO)).isFalse();
        assertThat(EstadoSolicitud.PENDIENTE_COORDINADOR.puedePasarA(EstadoSolicitud.RECHAZADO_RRHH)).isFalse();
    }

    @Test
    void ningunEstadoVuelveAtras() {
        assertThat(EstadoSolicitud.PENDIENTE_SUPERVISOR.puedePasarA(EstadoSolicitud.PENDIENTE_COORDINADOR)).isFalse();
        assertThat(EstadoSolicitud.PENDIENTE_COORDINADOR.puedePasarA(EstadoSolicitud.BORRADOR)).isFalse();
        assertThat(EstadoSolicitud.PENDIENTE_SUPERVISOR.puedePasarA(EstadoSolicitud.BORRADOR)).isFalse();
    }

    @Test
    void nadieVuelveAlBorrador() {
        assertThat(Arrays.stream(EstadoSolicitud.values())
                .filter(estado -> estado.puedePasarA(EstadoSolicitud.BORRADOR)))
                .isEmpty();
    }

    @Test
    void soloLosDosEstadosDeBandejaCuentanComoPendientes() {
        assertThat(Arrays.stream(EstadoSolicitud.values())
                .filter(EstadoSolicitud::estaPendiente))
                .containsExactlyInAnyOrder(
                        EstadoSolicitud.PENDIENTE_COORDINADOR,
                        EstadoSolicitud.PENDIENTE_SUPERVISOR);
    }
}
