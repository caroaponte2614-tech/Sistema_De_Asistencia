package com.schoolplus.configuration;

import com.schoolplus.model.Rol;
import com.schoolplus.services.auditoria.AuditoriaAccesoService;
import com.schoolplus.services.auth.RateLimitService;
import com.schoolplus.services.login.LoginAuditHandlers;
import com.schoolplus.services.login.UsuarioDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import javax.sql.DataSource;

/**
 * Configuración de seguridad. Hito 2 — auth completo.
 *
 * <p>Origen: un proyecto de referencia (configuration/SecurityConfig.java), que es la versión sana de
 * las de referencia (las otras traen {@code csrf.disable()}). Se porta su patrón
 * de CSRF por cookie, su {@code formLogin} y su enrutado por rol; cambian las rutas, los
 * roles y todo lo que la auditoría marcó como corregible.</p>
 *
 * <p>Lo que se añade respecto al original (docs/DECISIONES.md):</p>
 * <ul>
 *   <li>Límite de intentos de login, delante del filtro de autenticación.</li>
 *   <li>Bitácora de accesos en entrada, fallo y salida.</li>
 *   <li>Cabeceras explícitas: CSP, HSTS, {@code frame-ancestors}, política de referente.</li>
 *   <li>"Mantener sesión activa" con token persistente en base de datos, no en memoria:
 *       el §5.3 exige que el nodo no guarde estado.</li>
 *   <li>Rotación del identificador de sesión al entrar (fijación de sesión).</li>
 * </ul>
 */
@Configuration
// Habilita @PreAuthorize. Es lo que permite que la matriz de permisos del §3.1 —que
// vive en la tabla rol_capacidad— decida de verdad y no se quede en un adorno: las
// reglas por URL de aquí abajo cubren el rol, y @PreAuthorize cubre la capacidad.
@EnableMethodSecurity
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;
    private final LoginAuditHandlers auditHandlers;
    private final RateLimitService rateLimitService;
    private final AuditoriaAccesoService auditoriaAccesoService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService,
                          LoginAuditHandlers auditHandlers,
                          RateLimitService rateLimitService,
                          AuditoriaAccesoService auditoriaAccesoService) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.auditHandlers = auditHandlers;
        this.rateLimitService = rateLimitService;
        this.auditoriaAccesoService = auditoriaAccesoService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PersistentTokenRepository tokenRepository,
            @Value("${app.security.remember-me.key}") String claveRecordatorio,
            @Value("${app.security.remember-me.dias:14}") int diasRecordatorio) throws Exception {

        /*
         * CSRF activo con cookie legible por JavaScript (XSRF-TOKEN) + cabecera
         * X-XSRF-TOKEN. Los formularios de Thymeleaf reciben el campo _csrf oculto solo si
         * usan th:action, cosa que todas las plantillas de auth hacen.
         * Origen: un proyecto de referencia (SecurityConfig.java:38-45).
         */
        CsrfTokenRequestAttributeHandler manejadorCsrf = new CsrfTokenRequestAttributeHandler();
        manejadorCsrf.setCsrfRequestAttributeName(null); // carga diferida del token

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(manejadorCsrf))

                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "img-src 'self' data:; "
                                        + "style-src 'self' https://fonts.googleapis.com; "
                                        + "font-src 'self' https://fonts.gstatic.com; "
                                        + "form-action 'self'; "
                                        + "base-uri 'self'; "
                                        + "object-src 'none'; "
                                        + "frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L)))

                .authorizeHttpRequests(auth -> auth
                        // ── Público: las vistas de autenticación y lo estático ──────────
                        .requestMatchers(
                                "/", "/login", "/registro", "/verificar",
                                "/recuperar", "/restablecer",
                                "/main.css", "/js/**", "/images/**", "/favicon.ico", "/favicon.svg", "/error")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Verificación pública de certificados laborales: alguien escaneando
                        // el QR de un papel impreso no tiene por qué tener sesión iniciada.
                        .requestMatchers("/certificados/verificar/**").permitAll()

                        // ── Por rol ─────────────────────────────────────────────────────
                        // ADMIN entra a todo lo de abajo también: es el rol que cubre a
                        // cualquier otro cuando hace falta, no solo lo suyo propio.
                        .requestMatchers("/admin/**").hasRole(Rol.ADMIN)
                        .requestMatchers("/talento-humano/**").hasAnyRole(Rol.SUPERVISOR, Rol.ADMIN)
                        .requestMatchers("/coordinacion/**").hasAnyRole(Rol.COORDINADOR, Rol.ADMIN)
                        // El espacio personal lo tiene todo el mundo: los cuatro roles son
                        // también colaboradores, con sus permisos y sus vacaciones.
                        // El portal es la pestaña inicial de los cuatro (§4.7), y el
                        // tablero es de cada quien (§4.3). Quién puede hacer QUÉ dentro lo
                        // decide la matriz de permisos vía @PreAuthorize, no esta lista.
                        .requestMatchers("/mi-espacio/**", "/portal/**", "/portal",
                                "/api/v1/tablero/**", "/api/v1/notificaciones/**", "/ws/**",
                                "/sin-permiso").authenticated()

                        .anyRequest().authenticated())

                .formLogin(form -> form
                        .loginPage("/")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(auditHandlers.exito())
                        .failureHandler(auditHandlers.fallo())
                        .permitAll())

                .rememberMe(recordar -> recordar
                        .key(claveRecordatorio)
                        .rememberMeParameter("recordarme")
                        .rememberMeCookieName("SCHOOL_PLUS_RECUERDAME")
                        .tokenRepository(tokenRepository)
                        .tokenValiditySeconds(diasRecordatorio * 24 * 60 * 60)
                        .userDetailsService(usuarioDetailsService))

                // logoutUrl con CSRF activo ya exige POST; el formulario de salida del
                // panel manda el token oculto.
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(auditHandlers.salida())
                        .logoutSuccessUrl("/?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("SESSION", "SCHOOL_PLUS_RECUERDAME"))

                .sessionManagement(sesion -> sesion
                        // Rotar el id al autenticarse cierra la fijación de sesión: el
                        // identificador que alguien pudo plantar antes del login deja de valer.
                        .sessionFixation(fijacion -> fijacion.changeSessionId())
                        .invalidSessionUrl("/"))

                .exceptionHandling(excepciones -> excepciones
                        .accessDeniedPage("/sin-permiso"))

                .addFilterBefore(
                        new RateLimitLoginFilter(rateLimitService, auditoriaAccesoService),
                        UsernamePasswordAuthenticationFilter.class)

                .userDetailsService(usuarioDetailsService);

        return http.build();
    }

    /**
     * Tokens de "mantener sesión activa" en base de datos (tabla {@code persistent_logins},
     * creada por la migración V3).
     *
     * <p>La alternativa —{@code TokenBasedRememberMeServices}— firma la cookie con una clave
     * y no guarda nada, pero entonces no hay forma de revocar un recordatorio concreto: al
     * cambiar la contraseña, la cookie vieja seguiría entrando. Con tabla sí se revoca.</p>
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repositorio = new JdbcTokenRepositoryImpl();
        repositorio.setDataSource(dataSource);
        // La tabla la crea Flyway (V3). Cero DDL fuera de migraciones (§5.1).
        repositorio.setCreateTableOnStartup(false);
        return repositorio;
    }

    /** Necesario para que Spring Session indexe las sesiones por nombre de principal. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
