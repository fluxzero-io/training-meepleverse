package io.fluxzero.training.external.supplier;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.configuration.ApplicationProperties;
import io.fluxzero.sdk.tracking.handling.HandleSchedule;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import io.fluxzero.sdk.web.WebRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Path("/supplier")
public class SupplierEndpoint {
    private static final Random random = new SecureRandom();

    @HandlePost("order")
    void backorder(BackorderItems body) {
        log.info("Validating order {}", body);
        switch (body.getReference()) {
            case "supplyError":
                throw new IllegalStateException("Could not even");
            case "supplyInvalid":
                throw new IllegalCommandException("Item not found");
            default:
                body.getItems().forEach(itemId -> Fluxzero.schedule(new BackorderFulfilled(itemId, body.getOrderId()), Duration.ofMillis(random.nextLong(10000L))));
        }
    }

    @HandleSchedule
    void handle(BackorderFulfilled schedule) {
        Fluxzero.sendWebRequest(
            WebRequest.post("%s/supply/arrival/%s/%s".formatted(ApplicationProperties.requireProperty("meepleverse.url"), schedule.orderId(), schedule.itemId()))
                .build()
        );
    }
}
