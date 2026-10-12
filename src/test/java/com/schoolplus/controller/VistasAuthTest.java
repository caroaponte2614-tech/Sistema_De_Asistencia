package com.schoolplus.controller;

import com.schoolplus.configuration.SecurityConfig;
import com.schoolplus.repository.ClaseRepository;
import com.schoolplus.repository.UsuarioRepository;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import com.schoolplus.services.auth.RateLimitService;
import com.schoolplus.services.auth.RegisterService;
import com.schoolplus.services.auth.ValidationService;
import com.schoolplus.services.login.LoginAuditHandlers;
import com.schoolplus.services.login.UsuarioDetailsService;
import com.schoolplus.services.password.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Las tres vistas públicas de autenticación: que salen sin sesión, que llevan el diseño del
 * prototipo y que las rutas privadas siguen cerradas.
 *
 * <p>Es un test de slice y no un {@code @SpringBootTest} porque el contexto completo exige
 * MySQL: Flyway y {@code ddl-auto=validate} corren contra la base real.</p>
 */
@WebMvcTest(controllers = {LoginController.class, RegistroController.class, PasswordController.class})
@Import({SecurityConfig.class, LoginAuditHandlers.class})
// Spring Boot 4 sacó la autoconfiguración de seguridad del slice @WebMvcTest: sin esto no
// existe el bean HttpSecurity que necesita SecurityConfig.
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@TestPropertySource(properties = {
        "app.security.remember-me.key=clave-solo-para-el-test"
})
class VistasAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;
    @MockitoBean
    private AuditoriaAccesoService auditoriaAccesoService;
    @MockitoBean
    private RateLimitService rateLimitService;
    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private ClaseRepository claseRepository;
    @MockitoBean
    private RegisterService registerService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private PasswordResetService passwordResetService;
    @MockitoBean
    private DataSource dataSource;

    // @WebMvcTest instancia TODOS los @ControllerAdvice del proyecto, no solo los que
    // aplican a estos controladores, así que ShellAdvice también necesita sus piezas.
    @MockitoBean
    private com.schoolplus.services.cuenta.CuentaService cuentaService;

    @Test
    void elLoginEsPublicoYUsaElDisenoDelPrototipo() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa a tu cuenta")))
                // Tarjeta centrada, sin el panel lateral de marca que tenía antes.
                .andExpect(content().string(not(containsString("en un solo lugar"))))
                // Tokens del sistema Modernist, no los del dashboard índigo.
                .andExpect(content().string(containsString("var(--color-accent)")))
                .andExpect(content().string(containsString("family=Archivo")))
                // El formulario apunta donde espera SecurityConfig.
                .andExpect(content().string(containsString("action=\"/login\"")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"recordarme\"")))
                // Y lleva el campo oculto de CSRF, que es lo que hace que el POST funcione.
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void elRegistroYLaRecuperacionSonPublicosYComparteElMismoMarco() throws Exception {
        mockMvc.perform(get("/registro"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crea tu cuenta")))
                .andExpect(content().string(containsString("family=Archivo")));

        mockMvc.perform(get("/recuperar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("¿Olvidaste tu contraseña?")))
                .andExpect(content().string(containsString("family=Archivo")));
    }

    @Test
    void lasVistasDeAuthLlevanLasCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("https://fonts.googleapis.com")))
                .andExpect(header().string("Referrer-Policy", containsString("same-origin")));
    }

    @Test
    void unaRutaDeRolMandaAlLoginSiNoHaySesion() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
