package com.schoolplus.services.login;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** "Los 4 roles entran y ven lo suyo": este es el mapa que lo decide. */
class RutasPorRolTest {

    @Test
    void todoElMundoAterrizaEnElPortalAlIniciarSesion() {
        // El portal es la pestaña inicial de los cuatro roles (§4.7). El panel de cada
        // rol sigue existiendo, pero se alcanza desde el menú.
        assertThat(RutasPorRol.trasIniciarSesion()).isEqualTo("/portal");
    }

    @Test
    void cadaRolTieneSuPanelEnElMenu() {
        assertThat(destino("ROLE_ADMIN")).isEqualTo("/admin");
        assertThat(destino("ROLE_SUPERVISOR")).isEqualTo("/talento-humano");
        assertThat(destino("ROLE_COORDINADOR")).isEqualTo("/coordinacion");
        assertThat(destino("ROLE_USUARIO")).isEqualTo("/mi-espacio");
    }

    @Test
    void quienAcumulaRolesAterrizaEnElDeMasAlcance() {
        assertThat(destino("ROLE_USUARIO", "ROLE_COORDINADOR")).isEqualTo("/coordinacion");
        assertThat(destino("ROLE_USUARIO", "ROLE_SUPERVISOR")).isEqualTo("/talento-humano");
        assertThat(destino("ROLE_ADMIN", "ROLE_COORDINADOR", "ROLE_USUARIO")).isEqualTo("/admin");
    }

    @Test
    void sinRolesReconocidosSeVaAlEspacioPersonal() {
        assertThat(destino()).isEqualTo("/mi-espacio");
    }

    private String destino(String... autoridades) {
        return RutasPorRol.destinoDe(List.of(autoridades).stream()
                .map(SimpleGrantedAuthority::new)
                .toList());
    }
}
