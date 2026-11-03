package io.fluxzero.training.external.shipper;

import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Path("/shipper")
public class ShipperEndpoint {

    @HandlePost("send")
    void ship(SendShipment body) {
        log.info("Validating shipment {}", body);
        switch (body.getDetails().addressee().fullName()) {
            case "shipmentError":
                throw new IllegalStateException("Shipment error");
            case "shipmentInvalid":
                throw new IllegalCommandException("Invalid addressee");
        }
    }
}
