package com.schoolplus.services.certificados;

import java.time.LocalDateTime;

/** Lo que ve un tercero en la página pública de verificación — datos enmascarados a propósito. */
public record CertificadoVerificacion(String nombreEnmascarado, String cedulaEnmascarada, LocalDateTime emitidoEn) {
}
