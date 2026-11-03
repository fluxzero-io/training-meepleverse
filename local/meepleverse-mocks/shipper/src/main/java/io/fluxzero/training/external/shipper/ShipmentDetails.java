package io.fluxzero.training.external.shipper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShipmentDetails(@NotBlank String itemId, @NotBlank String orderId, @NotNull @Valid Addressee addressee) {
}
