package io.fluxzero.training.external.psp;

public record PspStatus(String paymentId, String currency, boolean accepted) {
}
