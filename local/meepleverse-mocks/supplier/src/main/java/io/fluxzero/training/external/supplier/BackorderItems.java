package io.fluxzero.training.external.supplier;

import lombok.Value;

import java.util.List;

@Value
public class BackorderItems {
    List<String> items;
    String orderId;
    String reference;
}
