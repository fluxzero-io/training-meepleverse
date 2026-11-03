package io.fluxzero.training.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxzero.common.Registration;
import io.fluxzero.common.TaskScheduler;
import io.fluxzero.common.ThrowingRunnable;
import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.configuration.DefaultFluxzero;
import io.fluxzero.sdk.configuration.client.LocalClient;
import io.fluxzero.sdk.scheduling.Schedule;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.web.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the same contracts against unchanged original bytecode and reconstructed source. */
class ServiceCompatibilityTest {
    private static final String PREFIX = "io.fluxzero.training.external.";
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");
    private static final Map<String, List<String>> CLASSES = Map.of(
            "psp", List.of("PaymentServiceProvider", "psp.PspEndpoint", "psp.PspStatus"),
            "supplier", List.of("Supplier", "supplier.SupplierEndpoint", "supplier.BackorderItems", "supplier.BackorderFulfilled"),
            "shipper", List.of("Shipper", "shipper.ShipperEndpoint", "shipper.SendShipment", "shipper.ShipmentDetails", "shipper.Addressee"));
    private final Map<String, URLClassLoader> loaders = new HashMap<>();

    enum Variant { ORIGINAL, RECONSTRUCTED }

    @AfterEach
    void closeFixturesAndLoaders() throws Exception {
        TestFixture.shutDownActiveFixtures();
        for (var loader : loaders.values()) loader.close();
    }

    @Test
    void originalArchivesAreUnchanged() throws Exception {
        var expected = Map.of(
                "psp", "12d8b962a586f78d1738c9184a9aeed34e4274bc07bb38b5cd72cf3c24015a25",
                "supplier", "8937e774377ffd5fefb73b88f8297e9ad1e0d28c529e367a52adb8886ef87888",
                "shipper", "db740e2b827f85e2abb728588efed0f075c531bd5ed259709fd3e128bc3d58c7");
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(originalJar(entry.getKey())))));
        }
    }

    @Test
    void allTwelveClassesRetainTheirPublicAndHandlerContracts() throws Exception {
        for (var entry : CLASSES.entrySet()) {
            for (String name : entry.getValue()) {
                Class<?> original = type(Variant.ORIGINAL, entry.getKey(), name);
                Class<?> reconstructed = type(Variant.RECONSTRUCTED, entry.getKey(), name);
                assertNotSame(original, reconstructed);
                assertEquals(api(original), api(reconstructed), name);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void pspReturnsTheSamePaymentJsonAndRequestsTheOriginalTimeout(Variant variant) throws Exception {
        var scheduler = new CapturingScheduler();
        try (var fluxzero = DefaultFluxzero.builder().replaceTaskScheduler(clock -> scheduler).build(LocalClient.newInstance())) {
            Object endpoint = instance(variant, "psp", "psp.PspEndpoint");
            CompletableFuture<String> result = fluxzero.apply(f -> invoke(endpoint, "validate", "payment-42"));
            assertEquals(2, scheduler.tasks.size());
            long delay = scheduler.tasks.getFirst().deadline - NOW.toEpochMilli();
            assertTrue(delay >= 0 && delay < 10_000, "PSP delay remains in [0, 10000) ms");
            assertEquals(NOW.plusSeconds(8).toEpochMilli(), scheduler.tasks.get(1).deadline);
            assertFalse(result.isDone());
            scheduler.run(0);
            assertEquals(JsonUtils.fromJson("{\"paymentId\":\"payment-42\",\"currency\":\"EUR\",\"accepted\":true}", JsonNode.class),
                    JsonUtils.fromJson(result.join(), JsonNode.class));
            scheduler.run(1);
            assertFalse(result.isCompletedExceptionally(), "A late timeout cannot replace success");
        }
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void pspTimeoutWinsWhenItsCallbackRunsFirst(Variant variant) throws Exception {
        var scheduler = new CapturingScheduler();
        try (var fluxzero = DefaultFluxzero.builder().replaceTaskScheduler(clock -> scheduler).build(LocalClient.newInstance())) {
            Object endpoint = instance(variant, "psp", "psp.PspEndpoint");
            CompletableFuture<String> result = fluxzero.apply(f -> invoke(endpoint, "validate", "payment-late"));
            scheduler.run(1);
            assertInstanceOf(TimeoutException.class, assertThrows(CompletionException.class, result::join).getCause());
            scheduler.run(0);
            assertTrue(result.isCompletedExceptionally(), "Late acceptance must not replace the timeout");
        }
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void supplierAcceptsTheMeepleversePayloadAndSchedulesEveryItem(Variant variant) throws Exception {
        Object endpoint = instance(variant, "supplier", "supplier.SupplierEndpoint");
        ObjectNode body = sample("supply/backorder-order1.json");
        body.putArray("items").add("gloomhaven").add("ark-nova").add("gloomhaven");
        TestFixture.create(endpoint).atFixedTime(NOW)
                .whenPost("/supplier/order", body)
                .expectWebResult(response -> response.getStatus() == 204)
                .expectOnlyNewSchedules(scheduledItem("gloomhaven"), scheduledItem("ark-nova"), scheduledItem("gloomhaven"));
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void supplierEmptyOrderSchedulesNothing(Variant variant) throws Exception {
        ObjectNode body = sample("supply/backorder-order1.json");
        body.putArray("items");
        TestFixture.create(instance(variant, "supplier", "supplier.SupplierEndpoint"))
                .whenPost("/supplier/order", body).expectWebResult(response -> response.getStatus() == 204)
                .expectOnlyNewSchedules();
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void supplierPreservesBothIntentionalFailuresAndNullBehavior(Variant variant) throws Exception {
        Object endpoint = instance(variant, "supplier", "supplier.SupplierEndpoint");
        for (String reference : List.of("supplyError", "supplyInvalid")) {
            ObjectNode body = sample("supply/backorder-order1.json").put("reference", reference);
            Object request = JsonUtils.convertValue(body, type(variant, "supplier", "supplier.BackorderItems"));
            Exception failure = assertThrows(Exception.class, () -> invoke(endpoint, "backorder", request));
            assertEquals(reference.equals("supplyError") ? IllegalStateException.class : IllegalCommandException.class, failure.getClass());
            assertEquals(reference.equals("supplyError") ? "Could not even" : "Item not found", failure.getMessage());
        }
        Object request = JsonUtils.convertValue(sample("supply/backorder-order1.json").putNull("reference"),
                type(variant, "supplier", "supplier.BackorderItems"));
        assertThrows(NullPointerException.class, () -> invoke(endpoint, "backorder", request));
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void supplierCallbackMatchesMeepleversesArrivalRoute(Variant variant) throws Exception {
        Object schedule = type(variant, "supplier", "supplier.BackorderFulfilled")
                .getConstructor(String.class, String.class).newInstance("gloomhaven", "order1");
        for (String base : List.of("", "https://meepleverse.example")) {
            Object endpoint = instance(variant, "supplier", "supplier.SupplierEndpoint");
            // Invoke directly: fixture schedule deserialization cannot select between identical
            // class names loaded by two different class loaders.
            TestFixture.create(endpoint).withProperty("meepleverse.url", base)
                    .whenExecuting(f -> invoke(endpoint, "handle", schedule))
                    .expectOnlyWebRequests(WebRequest.post(base + "/supply/arrival/order1/gloomhaven").build());
        }
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void shipperAcceptsMeepleversesShipmentPayload(Variant variant) throws Exception {
        ObjectNode event = sample("shipping/order1-shipped.json");
        TestFixture.create(instance(variant, "shipper", "shipper.ShipperEndpoint"))
                .whenPost("/shipper/send", event).expectWebResult(response -> response.getStatus() == 204);
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void shipperPreservesBothIntentionalFailuresAndNullBehavior(Variant variant) throws Exception {
        Object endpoint = instance(variant, "shipper", "shipper.ShipperEndpoint");
        for (String name : List.of("shipmentError", "shipmentInvalid")) {
            ObjectNode body = sample("shipping/order1-shipped.json");
            ((ObjectNode) body.path("details").path("addressee")).put("fullName", name);
            Object request = JsonUtils.convertValue(body, type(variant, "shipper", "shipper.SendShipment"));
            Exception failure = assertThrows(Exception.class, () -> invoke(endpoint, "ship", request));
            assertEquals(name.equals("shipmentError") ? IllegalStateException.class : IllegalCommandException.class, failure.getClass());
            assertEquals(name.equals("shipmentError") ? "Shipment error" : "Invalid addressee", failure.getMessage());
        }
        ObjectNode body = sample("shipping/order1-shipped.json");
        ((ObjectNode) body.path("details").path("addressee")).putNull("fullName");
        Object request = JsonUtils.convertValue(body, type(variant, "shipper", "shipper.SendShipment"));
        assertThrows(NullPointerException.class, () -> invoke(endpoint, "ship", request));
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void lombokValuesKeepConstructorBindingEqualityAndJson(Variant variant) throws Exception {
        for (var entry : Map.of("supplier", "supplier.BackorderItems", "shipper", "shipper.SendShipment").entrySet()) {
            ObjectNode json = sample(entry.getKey().equals("supplier") ? "supply/backorder-order1.json" : "shipping/order1-shipped.json");
            Class<?> target = type(variant, entry.getKey(), entry.getValue());
            Object first = JsonUtils.convertValue(json, target), second = JsonUtils.convertValue(json, target);
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertEquals(json, JsonUtils.convertValue(first, JsonNode.class));
        }
    }

    private Predicate<Schedule> scheduledItem(String item) {
        return schedule -> {
            JsonNode payload = JsonUtils.convertValue(schedule.getPayload(), JsonNode.class);
            return payload.path("itemId").asText().equals(item) && payload.path("orderId").asText().equals("order1")
                    && !schedule.getDeadline().isBefore(NOW) && schedule.getDeadline().isBefore(NOW.plusSeconds(10));
        };
    }

    private ObjectNode sample(String path) throws Exception {
        ObjectNode result = (ObjectNode) JsonUtils.fromJson(Files.readString(Path.of(System.getProperty("meepleverseDirectory"), "src/test/resources", path)), JsonNode.class);
        result.remove("@class");
        return result;
    }

    private Object instance(Variant variant, String service, String name) throws Exception {
        return type(variant, service, name).getConstructor().newInstance();
    }

    private Class<?> type(Variant variant, String service, String name) throws Exception {
        if (variant == Variant.RECONSTRUCTED) return Class.forName(PREFIX + name);
        if (loaders.containsKey(service)) return loaders.get(service).loadClass(PREFIX + name);
        URL classes = new URL("jar:" + originalJar(service).toUri() + "!/BOOT-INF/classes/");
        URLClassLoader loader = new URLClassLoader(new URL[]{classes}, getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (!className.startsWith(PREFIX)) return super.loadClass(className, resolve);
                Class<?> result = findLoadedClass(className);
                if (result == null) result = findClass(className);
                if (resolve) resolveClass(result);
                return result;
            }
        };
        loaders.put(service, loader);
        return loader.loadClass(PREFIX + name);
    }

    private Path originalJar(String service) {
        return Path.of(System.getProperty("originalJarsDirectory"), service, service + ".jar");
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String name, Object argument) throws Exception {
        var method = Arrays.stream(target.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true);
        try {
            return (T) method.invoke(target, argument);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) throw exception;
            throw e;
        }
    }

    private Set<String> api(Class<?> type) {
        Set<String> result = new TreeSet<>();
        result.add(Modifier.toString(type.getModifiers()));
        for (var annotation : type.getAnnotations()) result.add(annotation.toString());
        for (var constructor : type.getConstructors()) {
            result.add("constructor " + Arrays.toString(Arrays.stream(constructor.getParameterTypes()).map(Class::getName).toArray()));
            for (var annotation : constructor.getAnnotations()) result.add(annotation.toString());
        }
        for (var method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) continue;
            result.add(method.getName() + " " + method.getReturnType().getName() + " "
                    + Arrays.toString(Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray()));
            for (var annotation : method.getAnnotations()) result.add(method.getName() + annotation);
            for (var parameter : method.getParameters()) {
                result.add(method.getName() + " parameter " + parameter.getName() + " " + Arrays.toString(parameter.getAnnotations()));
            }
        }
        return result;
    }

    /** Captures the real requested deadlines; invokes callbacks in a chosen order without wall-clock sleeps. */
    private static class CapturingScheduler implements TaskScheduler {
        private final List<Task> tasks = new ArrayList<>();
        private record Task(long deadline, ThrowingRunnable callback) {}
        @Override public Registration schedule(long deadline, ThrowingRunnable task) {
            tasks.add(new Task(deadline, task));
            return () -> {};
        }
        @Override public Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Override public void executeExpiredTasks() {}
        @Override public void shutdown() {}
        void run(int index) throws Exception { tasks.get(index).callback.run(); }
    }
}
