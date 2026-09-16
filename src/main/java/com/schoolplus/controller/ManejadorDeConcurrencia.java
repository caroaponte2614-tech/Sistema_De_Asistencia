package com.schoolplus.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce los choques de concurrencia a <strong>409 Conflict</strong>.
 *
 * <p>Requisito literal del §5.2: "Manejo explícito de {@code OptimisticLockException} →
 * respuesta {@code 409 Conflict} con mensaje entendible en el front, <em>no un 500</em>".
 * Sin esto, dos personas tocando la misma tarjeta o la misma solicitud producen un error
 * genérico que no dice nada y que además parece un fallo del servidor cuando no lo es.</p>
 *
 * <p>Un 409 aquí no es un error: es la respuesta correcta a "alguien se te adelantó".
 * Por eso se registra como {@code warn} y no como {@code error}, y por eso el mensaje
 * dice qué hacer —recargar— en vez de describir la excepción.</p>
 */
// Acotado a los controladores REST del tablero a propósito: responden JSON, y devolver JSON
// desde una pantalla Thymeleaf dejaría al usuario mirando un objeto en crudo. El muro, que
// es MVC, resuelve sus conflictos con un mensaje en pantalla.
@RestControllerAdvice(assignableTypes = {TableroApiController.class, TableroAreaApiController.class})
public class ManejadorDeConcurrencia {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeConcurrencia.class);

    private static final String MENSAJE =
            "Alguien cambió esto mientras lo tenías abierto. Recarga la página para ver "
                    + "la versión actual y vuelve a intentarlo.";

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflicto(OptimisticLockingFailureException e,
                                                         HttpServletRequest peticion) {
        log.warn("Conflicto de concurrencia en {} {}: {}",
                peticion.getMethod(), peticion.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "conflicto", "mensaje", MENSAJE));
    }

    /** Datos de entrada inválidos del tablero: 400 con el motivo, no 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "datos_invalidos", "mensaje", e.getMessage()));
    }
}
