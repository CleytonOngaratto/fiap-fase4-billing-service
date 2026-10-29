package br.com.fiap.postech.carworkshop.billing.adapter.presenter;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error) {

    public static ErrorResponse of(int status, String error) {
        return new ErrorResponse(Instant.now(), status, error);
    }
}
