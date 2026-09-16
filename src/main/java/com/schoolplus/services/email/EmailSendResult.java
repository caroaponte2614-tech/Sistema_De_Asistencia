package com.schoolplus.services.email;

/** Origen: un proyecto de referencia (services/email/EmailSendResult.java) — se porta tal cual. */
public record EmailSendResult(boolean sent, String message) {

    public static EmailSendResult success() {
        return new EmailSendResult(true, "Correo enviado correctamente.");
    }

    public static EmailSendResult failed(String message) {
        return new EmailSendResult(false, message);
    }
}
