# Meepleverse exercise services

Reconstructed Java sources for the PSP, supplier and shipper used by Meepleverse.
The twelve application classes were recovered from the original JARs in
`../psp`, `../supplier` and `../shipper`, with Vineflower 1.12.0 and bytecode inspection.
Generated Lombok code has been restored to `@Value` and `@Slf4j` declarations.
The embedded module POMs supplied the Maven coordinates; the missing parent build
has been recreated for Java 25, SDK **1.292.48** and Spring Boot **4.1.2**.

This is a reconstruction, not a recovered copy of the original Git repository.
Comments, formatting and the exact original parent POM cannot be recovered from
these binaries. The original JARs are retained unchanged as comparison references.

## Run with Meepleverse

From the repository root, on macOS/Linux or Windows PowerShell:

```text
fz dev --background --dev-server-version 1.20.6
fz dev status --json
```

The root `.fluxzero/dev.yaml` starts four applications on one SDK 1.x runtime:
Meepleverse, PSP, supplier and shipper. The supplier's `MEEPLEVERSE_URL` is an
explicit empty string so callbacks use `/supply/arrival/{orderId}/{itemId}` on
that shared runtime. When running against a separate Meepleverse host, set it
to the host's HTTP base URL instead.

Use the proxy URL reported by `fz dev status --json` for requests. Stop with
`fz dev stop`. The Dev Server compiles and watches these sources; do not run a
second Maven build while it is active. Native Windows execution still needs
qualification; the configuration does not require Bash, WSL or Docker.

## Preserved exercise behavior

| Service | Route | Behavior |
|---|---|---|
| PSP | `POST /psp/validate/{paymentId}` | Completes with EUR/accepted after a random delay in `[0, 10000)` ms, but times out after 8 seconds. |
| Supplier | `POST /supplier/order` | Schedules a callback per item, including duplicates, after a random delay in `[0, 10000)` ms. |
| Shipper | `POST /shipper/send` | Accepts shipment details without maintaining shipment state. |

Supplier reference `supplyError` throws `IllegalStateException("Could not even")`;
`supplyInvalid` throws `IllegalCommandException("Item not found")`. Shipper addressee
names `shipmentError` and `shipmentInvalid` produce the corresponding technical
and functional failures. Null reference/name behavior is also retained.
With the current SDK, successful void handlers return HTTP 204, functional
failures 403 and technical failures 500.

The original PSP parameter has **no `@PathParam` annotation**. It binds the
request body, not the URL parameter, on the current SDK. Meepleverse sends an
empty body and reads only `accepted`, so that flow works, but the returned JSON
omits `paymentId`. Sending `{}` instead is not equivalent: it caused a
deserialization failure and no response in the local integration check. This
quirk is preserved rather than silently corrected during source reconstruction.

## Compatibility checks

`compatibility-tests` loads the original application classes directly from the
retained JARs in separate class loaders. It runs the same cases against those
classes and the rebuilt classes: public/handler contracts, Meepleverse JSON
fixtures, Lombok value behavior, supplier schedules and callback URLs, intentional
failures, and both PSP success/timeout callback orders. The original JAR hashes
are checked too. These are bounded contract checks, not a proof of every possible
input or byte-for-byte source equality.

Both variants run with the **current** SDK and dependencies. The tests compare
application behavior on that baseline; they do not recreate the complete old
SDK 1.9.0 runtime. The callback comparison invokes its handler directly to avoid
fixture deserialization choosing between identically named classes from different
class loaders. A running Dev Server order flow separately checks real scheduling
and delivery to Meepleverse.

Without an active Dev Server, from the repository root:

```bash
./mvnw -f local/meepleverse-mocks/pom.xml verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd -f local/meepleverse-mocks/pom.xml verify
```

The reactor produces `psp/target/psp-exec.jar`,
`supplier/target/supplier-exec.jar` and `shipper/target/shipper-exec.jar`.
The unclassified JARs contain classes for the compatibility-test classpath;
use the `-exec.jar` variants with `java -jar`. Builds do not replace the original
JARs used by the historical Compose stack.
