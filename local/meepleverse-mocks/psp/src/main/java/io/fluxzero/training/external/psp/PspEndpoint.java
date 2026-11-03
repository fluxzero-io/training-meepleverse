package io.fluxzero.training.external.psp;

import io.fluxzero.common.TaskScheduler;
import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Path("/psp")
public class PspEndpoint {
    private static final Random random = new SecureRandom();

    @HandlePost("/validate/{paymentId}")
    CompletableFuture<String> validate(String paymentId) {
        log.info("Validating payment {}", paymentId);
        TaskScheduler taskScheduler = Fluxzero.get().taskScheduler();
        CompletableFuture<String> result = new CompletableFuture<>();
        taskScheduler.schedule(Duration.ofMillis(random.nextLong(10000L)), () -> result.complete(JsonUtils.asJson(new PspStatus(paymentId, "EUR", true))));
        return taskScheduler.orTimeout(result, Duration.ofSeconds(8L));
    }
}
