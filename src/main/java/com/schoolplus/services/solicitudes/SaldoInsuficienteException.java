package com.schoolplus.services.solicitudes;

import java.math.BigDecimal;

/** La solicitud de vacaciones excede los días disponibles (§4.5). */
public class SaldoInsuficienteException extends RuntimeException {

    public SaldoInsuficienteException(BigDecimal pedidos, BigDecimal disponibles) {
        super("Pediste " + pedidos.stripTrailingZeros().toPlainString()
                + " días hábiles y tienes " + disponibles.stripTrailingZeros().toPlainString()
                + " disponibles.");
    }
}
