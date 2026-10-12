package com.schoolplus.services.login;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** "Los roles entran y ven lo suyo": este es el mapa que lo decide. */
class RutasPorRolTest {

    @Test
    void cadaRolAterrizaEnSuPanel() {
        assertThat(destino("ROLE_ADMIN")).isEqualTo("/admin");
        assertThat(destino("ROLE_SUPERVISOR")).isEqualTo("/rectoria");
        assertThat(destino("ROLE_COORDINADOR")).isEqualTo("/coordinacion");
        assertThat(destino("ROLE_USUARIO")).isEqualTo("/mi-espacio");
    }

    @Test
    void elEstudianteAterrizaEnSuAsistencia() {
        assertThat(destino("ROLE_ESTUDIANTE")).isEqualTo("/estudiante/asistencia");
        assertThat(RutasPorRol.trasIniciarSesion(autoridades("ROLE_ESTUDIANTE")))
                .isEqualTo("/estudiante/asistencia");
    }

    @Test
    void trasIniciarSesionSeVaAlPanelDelRol() {
        assertThat(RutasPorRol.trasIniciarSesion(autoridades("ROLE_SUPERVISOR"))).isEqualTo("/rectoria");
    }

    @Test
    void quienAcumulaRolesAterrizaEnElDeMasAlcance() {
        assertThat(destino("ROLE_USUARIO", "ROLE_COORDINADOR")).isEqualTo("/coordinacion");
        assertThat(destino("ROLE_USUARIO", "ROLE_SUPERVISOR")).isEqualTo("/rectoria");
        assertThat(destino("ROLE_ADMIN", "ROLE_COORDINADOR", "ROLE_USUARIO")).isEqualTo("/admin");
        // Un docente que además estudia algo sigue siendo personal.
        assertThat(destino("ROLE_USUARIO", "ROLE_ESTUDIANTE")).isEqualTo("/mi-espacio");
    }

    @Test
    void sinRolesReconocidosSeVaAlEspacioPersonal() {
        assertThat(destino()).isEqualTo("/mi-espacio");
    }

    private String destino(String... autoridades) {
        return RutasPorRol.destinoDe(autoridades(autoridades));
    }

    private List<SimpleGrantedAuthority> autoridades(String... autoridades) {
        return List.of(autoridades).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
