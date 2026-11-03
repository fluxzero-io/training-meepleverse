# Meepleverse

This repository contains backend sources used in the **2-day in-person Flux Advanced Training**.

## SDK 1.x course baseline

The refreshed course uses **SDK 1.292.48**, pinned through `fluxzero.version` in `pom.xml`, and Java 25.
Dependencies are resolved from the public Fluxzero Packages Maven repository and Maven Central.
Keep this course on SDK 1.x when changing examples or consulting documentation.

The existing unit tests use in-memory fixtures and mock the external payment, supplier and shipping services.
They can be run without Docker using the Maven wrapper shown below.

For local development with the Fluxzero CLI, run `fz dev --background --dev-server-version 1.20.6` from this directory.
The Dev Server selects the matching SDK 1.x Test Server and Proxy, compiles the application and runs its tests.
The application initializes the catalog at startup. Authentication continues to use the training's `SenderProvider`.
This minimal development configuration starts the application; it does not start the three external exercise services.
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

Before continuing, make sure you have created your own fork of
the [public Meepleverse repository](https://github.com/fluxzero-io/training-meepleverse). Next, clone this fork on your
machine, then open the project in Intellij IDEA.

## Running the Application

### Prerequisites

- JDK 25
- Git and the Fluxzero CLI
- An IDE such as IntelliJ IDEA

### Dev Server

From the repository directory, in a terminal or Windows PowerShell:

```text
fz dev --background --dev-server-version 1.20.6
fz dev status --json
```

Open the reported gateway URL followed by `/_fluxzero/dev/` for the Devboard.
Use Workspace, Tests and Audit trail to inspect the application, test results and messages.
The Dev Server manages the local Test Server, Proxy and auditlog storage; a separate OpenSearch setup is not required.

The external exercise services still need to be integrated into this refreshed setup before the complete order exercises
can use this route. The historical IntelliJ `docker-compose` configuration remains available for reference.

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
