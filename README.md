# Meepleverse

This repository contains backend sources used in the **2-day in-person Flux Advanced Training**.

## SDK 1.x course baseline

The refreshed course uses **SDK 1.292.48**, pinned through `fluxzero.version` in `pom.xml`, and Java 25.
The build uses Spring Boot 4.1.2 and Maven 3.10.0 (via Maven Wrapper 3.3.4).
Spring Boot's version is independent of the Fluxzero SDK major version: this remains an SDK 1.x course.
Shared Jackson 2, JUnit, Logback and Jetty versions are aligned with the Fluxzero BOM in `pom.xml`.
Dependencies are resolved from the public Fluxzero Packages Maven repository and Maven Central.
Keep this course on SDK 1.x when changing examples or consulting documentation.

The existing unit tests use in-memory fixtures and mock the external payment, supplier and shipping services.
They can be run without Docker using the Maven wrapper shown below.

For local development with the Fluxzero CLI, run `fz dev --background --dev-server-version 1.20.6` from this directory.
The Dev Server selects the matching SDK 1.x Test Server and Proxy, compiles the applications and manages their tests.
The application initializes the catalog at startup. Authentication continues to use the training's `SenderProvider`.
The configuration starts Meepleverse and the PSP, supplier and shipper from source on the same local runtime.
Use the proxy address reported by `fz dev status --json` for the existing `/catalog`, `/users` and `/orders` routes.
Run `fz dev stop` to stop this local environment.

The Docker Compose configurations below are the historical course stack, still pinned to runtime/proxy 1.56.0.
They require separate qualification before use with the refreshed course. Do not start both runtime stacks for one exercise.

## Introduction

In this training you’ll step into the world of **Meepleverse**, an e-commerce webapp for board games.

Meepleverse began as a small side project: the owners of a local board game shop hacked together a site over a weekend
so their loyal customers wouldn’t have to bike across town to pick up games.

What started as a simple service for a few locals quickly exploded in popularity. Gamers from all over the world
discovered Meepleverse — and demand skyrocketed.

With success came chaos: performance bottlenecks, broken flows, and mysterious bugs. The overwhelmed store owners have
now called on you, a seasoned Flux practitioner, to help them debug, tune, and stabilize their platform.

Your mission: **make Meepleverse scale** while keeping the orders flowing.

## What you’ll learn

Over the course of two days, you will:

- Explore how Flux works under-the-hood
- Troubleshoot and fix production issues
- Repair errors retroactively in running systems
- Evolve applications while they are in production
- Discuss custom topics brought in by participants
- Review selected SDK 1.x improvements relevant to the exercises

## Setting up

For the November 2026 SDK 1.x course, follow the
[participant preparation guide](https://training.fluxzero.io/advanced/preparation/).
It covers Git, Fluxzero CLI installation on Windows/macOS/Linux, Java setup, IDE setup
and the readiness check. Use the course branch:

```text
git clone https://github.com/fluxzero-io/training-meepleverse.git
cd training-meepleverse
git switch chore/portbase-training-1x
```

A fork is optional. Open this folder in your Java IDE with JDK 25 selected for
both the project and Maven. The CLI baseline is 1.21.3; it starts the explicitly
selected Dev Server 1.20.6 without upgrading the application's SDK.

## Running the Application

### Prerequisites

- JDK 25; `fz dev` offers installation through WinGet or Homebrew if no suitable JDK is found
- Git and the Fluxzero CLI
- An IDE such as IntelliJ IDEA

### Dev Server

From the repository directory, in a terminal or Windows PowerShell:

```text
fz dev --background --dev-server-version 1.20.6
fz dev status --json
```

Run this in your own terminal. Accept the Java 25 installation prompt if shown;
without WinGet or Homebrew, follow the manual JDK instructions in the preparation guide.
The CLI downloads Dev Server and uses the repository's Maven wrapper; no separate Maven
installation or wrapper check is needed before the first start. Select JDK 25 in your IDE too.

Open the reported gateway URL followed by `/_fluxzero/dev/` for the Devboard.
Use Workspace, Tests and Audit trail to inspect the application, test results and messages.
The Dev Server manages the local Test Server, Proxy and auditlog storage; a separate OpenSearch setup is not required.

Complete one start before the course so the tools and dependencies are downloaded.
You are ready when all four applications run, the Meepleverse application tests
pass, Audit trail shows startup messages and `/catalog` returns 35 games.
Run `fz dev stop` afterwards; start again using the same command during the course.

The [exercise services](local/meepleverse-mocks/README.md) have been reconstructed from their original JARs and use
the same SDK 1.292.48 / Spring Boot 4.1.2 baseline. The supplier callback uses a relative URL on the shared runtime;
no fixed port or separately configured callback host is needed. Their intentional failures and delays remain intact.
The original JARs remain unchanged as reconstruction references and for the historical Compose configuration.
Those historical binaries still contain SDK 1.9.0 and Spring Boot 3.5.0; the Dev Server does not use them.

### API Endpoints

Once running, you can create new board game orders from `orders.http`.

## Testing

**Using IntelliJ IDEA:**

- Run `All tests`

**Using Maven:**

```bash
./mvnw test
```

On Windows, without an active Dev Server:

```powershell
.\mvnw.cmd test
```

When the Dev Server is active, follow its managed test results instead of starting a second build.
The separate exercise-service reactor also has [compatibility tests](local/meepleverse-mocks/README.md#compatibility-checks)
against the original binaries.
