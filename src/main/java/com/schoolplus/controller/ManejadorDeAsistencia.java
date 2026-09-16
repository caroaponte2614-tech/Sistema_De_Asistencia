package com.schoolplus.controller;

import com.schoolplus.services.asistencia.MotivoRechazo;
import com.schoolplus.services.asistencia.RegistroRechazadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce a JSON los "no" del módulo de asistencia.
 *
 * <p>Un QR vencido o un celular sin autorizar no son errores del servidor: son respuestas
 * del negocio. Salen como <strong>422</strong> con el título y el mensaje que la pantalla
 * del estudiante muestra tal cual, en vez de un 500 y un texto técnico.</p>
 *
 * <p>Acotado a la API de asistencia, igual que {@code ManejadorDeConcurrencia} lo está al
 * tablero: devolver JSON desde una pantalla Thymeleaf dejaría al usuario mirando un objeto
 * en crudo.</p>
 */
@RestControllerAdvice(assignableTypes = AsistenciaApiController.class)
public class ManejadorDeAsistencia {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeAsistencia.class);

    @ExceptionHandler(RegistroRechazadoException.class)
    public ResponseEntity<Map<String, Object>> rechazado(RegistroRechazadoException e) {
        return respuesta(HttpStatus.UNPROCESSABLE_ENTITY, e.motivo().name(),
                e.motivo().titulo(), e.motivo().mensaje());
    }

    /**
     * Dos peticiones del mismo estudiante a la vez: la comprobación anticipada dejó pasar a
     * las dos y la restricción única paró a la segunda. Para la persona es exactamente
     * "ya estabas registrado".
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> duplicado(DataIntegrityViolationException e) {
        log.warn("Registro de asistencia duplicado detectado por la base: {}", e.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, MotivoRechazo.YA_REGISTRADO.name(),
                MotivoRechazo.YA_REGISTRADO.titulo(), MotivoRechazo.YA_REGISTRADO.mensaje());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(IllegalArgumentException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "No se pudo registrar", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String motivo,
                                                          String titulo, String mensaje) {
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ok", false, "motivo", motivo, "titulo", titulo, "mensaje", mensaje));
    }
}
