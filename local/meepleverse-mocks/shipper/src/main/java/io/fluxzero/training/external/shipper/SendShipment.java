package io.fluxzero.training.external.shipper;

import lombok.Value;

@Value
public class SendShipment {
    String shipmentId;
    ShipmentDetails details;
}
