package com.schoolplus.dto;

import java.util.List;

/**
 * El estado completo de una sesión tal como lo pinta la pantalla de proyección: el QR de
 * esta ventana, el código, el contador y la lista de quienes ya se registraron.
 *
 * <p>Lo devuelve la API cada vez que el QR rota o el WebSocket avisa de un registro nuevo.
 * El QR viaja como {@code data:} URI para que refrescarlo no cueste otra petición.</p>
 */
public record SesionEnVivo(String sesionId,
                           String clase,
                           boolean abierta,
                           String enlace,
                           String qr,
                           String codigo,
                           int segundosParaRotar,
                           int rotacionSegundos,
                           long presentes,
                           long tardes,
                           long registrados,
                           long inscritos,
                           List<FilaRegistro> registros) {
}
