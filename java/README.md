# Booster for Java

> **A note before you read on.** I am Álvaro's Claude. Álvaro asked me to send these pages along as I reach the
> end of the Java translation, so he has **not** reviewed this one yet: what you are reading was written, tested and
> proofread by the same party that wrote the code, which is roughly as independent as a cook grading his own soup.
> In my defence, every command below was actually run before it was written down (on free ports instead of 3000 and
> 65529, with everything I started stopped again afterwards), except the ones that install software, write into
> `~/.m2` (I used the private-repository variant instead) or deploy to a real Azure subscription (nobody lent me one,
> wisely). If something still fails for you, the blame is mine, not his.

A faithful port of the [Booster Framework](https://boosterframework.com) to **Java 27**: event-sourced CQRS applications
whose GraphQL API (queries, mutations and subscriptions) is generated from the commands, read models and queries you
write. Everything lives under `java/`; the TypeScript framework under `packages/` is untouched and remains the source of
truth for behaviour.

Contents: [What is ported](#1-what-is-ported) · [Prerequisites](#2-prerequisites) · [Build and test](#3-build-and-test)
· [Run the sample application](#4-run-the-sample-application-locally) · [Your own application](#5-create-your-own-application-with-the-cli)
· [Azure](#6-azure) · [Rockets](#7-rockets) · [Module map](#8-module-map-and-architecture)
· [Testing](#9-testing-strategy-and-what-was-tested) · [Differences and limitations](#10-differences-from-typescript-and-known-limitations)

## 1. What is ported

| Area | Status |
| --- | --- |
| Core: commands, events, entities and snapshots, read models and projections (join keys, un-projections, sequenced read models, calculated fields), notifications, queries, event handlers, the event search API, event deletion and entity touching | ported, `framework-core` |
| Schema migrations (`@SchemaMigration`/`@ToVersion`) and data migrations (`@DataMigration`, `BoosterDataMigrations`) | ported |
| GraphQL API: generated schema, filters, sorting, pagination, `before` hooks, subscriptions over WebSocket (`graphql-ws`) | ported (graphql-java) |
| Authentication (`JwksUriTokenVerifier`, `PublicKeyTokenVerifier`, custom verifiers) and authorization (roles, custom authorizers) | ported (JDK crypto, RS256) |
| Scheduled commands, health sensors, tracing, global error handler, logging, retries | ported |
| Local provider: file-based persistent store in the NeDB format of the TypeScript provider, HTTP and WebSocket servers, scheduler, crash recovery | ported, plus durability guarantees TypeScript does not have |
| Azure provider: Cosmos DB, Event Hubs, Web PubSub and Azure Functions at run time; Terraform synthesis, packaging, deploy and nuke | ported, verified by offline contract tests (nothing is deployed) |
| CLI (`boost`): `new:*` generators, `add:*`, `stub:publish`, `build`, `clean`, `start`, `synth`, `deploy`, `nuke`, rocket commands | ported (Maven projects) |
| Rockets: webhook and file uploads, local and Azure | ported |
| AWS provider, AWS-only rockets (backup, static sites), Artillery load tests | **not ported** (out of scope) |

## 2. Prerequisites

* **JDK 27** or newer (the build enforces it) and **Maven 3.9** or newer. `git` for `boost new:project` (unless
  `--skipGit`). Terraform only for Azure deployments and the opt-in Terraform validation.
* macOS with Homebrew:

  ```bash
  brew install openjdk maven
  export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
  ```

* SDKMAN (Linux or macOS): `sdk list java` shows the JDK 27 builds; install one with `sdk install java <identifier>`
  and Maven with `sdk install maven`. SDKMAN sets `JAVA_HOME` for you.
* Check (Maven must report the same JDK):

  ```console
  $ "$JAVA_HOME/bin/java" -version
  openjdk version "27.0.1.0.3" 2026-10-24
  $ mvn -version
  Apache Maven 3.10.0 (...)
  Java version: 27.0.1.0.3, vendor: Homebrew, runtime: /opt/homebrew/Cellar/openjdk/27.0.1.0.3/libexec/openjdk.jdk/Contents/Home
  ```

  With an older JDK the build stops at once with `Booster for Java requires JDK 27 or newer. Point JAVA_HOME at a JDK
  27 installation.`

## 3. Build and test

Every command runs from `java/` with `JAVA_HOME` pointing at JDK 27. Nothing is installed into `~/.m2` by these
commands (only third-party dependencies are downloaded into it). Durations were measured on an Apple-silicon laptop on
2026-11-11 (the final runs of section 9), with other builds running on the same machine.

```bash
cd java
mvn -B verify          # build everything, run unit and in-JVM integration tests   (5 min 21 s)
mvn -B -Pe2e verify    # the same plus every end-to-end suite (*IT classes)        (10 min 30 s to 11 min 13 s)
```

| To run | Command |
| --- | --- |
| one module (with the modules it depends on, and their tests) | `mvn -B -pl framework-provider-azure -am verify` |
| one test class | `mvn -B -pl framework-core -am verify -Dtest=BoosterCommandDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false` |
| one test method | `mvn -B -pl framework-core -am verify '-Dtest=BoosterCommandDispatcherTest#failsWhenThereIsNoHandler' -Dsurefire.failIfNoSpecifiedTests=false` |
| one end-to-end suite (no unit tests) | `mvn -B -Pe2e -pl framework-integration-tests -am verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CartIT` |
| several suites or a pattern | the same with `'-Dit.test=CartIT,CommandsIT'` or `'-Dit.test=*FunctionalityIT'` |
| one CLI end-to-end class | `mvn -B -Pe2e -pl cli-integration-tests -am verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CliStartIT` |

`-am` also builds (and tests) the upstream modules; `-Dtest=...` with `-Dsurefire.failIfNoSpecifiedTests=false` keeps
the other modules from failing because they have no such test. Measured: one test class 5 s, one method
3 s, the Azure provider module 10 s, `CartIT` alone 8 s, `CliStartIT` alone
15 s (each including the compilation of what changed; measured on 2026-11-10). In the full `-Pe2e` runs,
`cli-integration-tests` took 1 min 10 s to 1 min 12 s and `framework-integration-tests` 4 min 7 s to 4 min 47 s, about
2.5 minutes of which is `ScheduledCommandsAfterRestartIT` waiting for three scheduler minutes.

Where the output goes:

* test reports: `<module>/target/surefire-reports/` (unit) and `<module>/target/failsafe-reports/` (`*IT`);
* every application an end-to-end suite launches: log in `framework-integration-tests/target/e2e-logs/<name>.log`,
  project directory (with its `.booster` data) in `target/e2e-projects/<name>-integration-sandbox`, deleted afterwards
  unless `-Dbooster.e2e.keepProjects=true`;
* every `boost` invocation of the CLI suite (arguments, exit code, both output streams):
  `cli-integration-tests/target/booster-it/logs/cli.log`; sandboxes kept with `-Dbooster.it.keepSandboxes=true`.

The suites choose free ports, so several checkouts can build at the same time, and they stop every process they start
(`jps -lm` shows no `com.boostercloud.integration.Main` afterwards).

## 4. Run the sample application locally

`framework-integration-tests` contains the sample application of the TypeScript integration suites ("my-store", a
cart demo with 35 commands, 18 entities, 19 read models, queries, roles, scheduled commands and migrations). Build it
and start it exactly as the CLI launches an application (`target/classes` plus the runtime jars the build copies to
`target/lib`, the project directory as working directory):

```bash
cd java
mvn -B -q -pl framework-integration-tests -am package -DskipTests
cd framework-integration-tests
BOOSTER_ENV=local BOOSTER_INTERNAL_LOCAL_PORT=3000 "$JAVA_HOME/bin/java" -cp 'target/classes:target/lib/*' \
    com.boostercloud.integration.Main start --port 3000
```

```text
... [INFO]: [Booster]|WebSocketServerAdapter#constructor#handleUpgrade: ["Starting websocket server on ws://localhost:65529/websocket"]
... [DEBUG]: [Booster]|events-adapter#recoverUndispatchedEvents: ["No undispatched events to recover"]
... [DEBUG]: [Booster]|scheduler#configureScheduler: ["Scheduled command CheckCartCount scheduled with cron '0/1 * * * *'"]
... [INFO]: [Booster]|LocalInfrastructure#start: ["Local server listening on http://localhost:3000 (GraphQL: http://localhost:3000/graphql, health: http://localhost:3000/sensor/health/, websocket: ws://localhost:65529/websocket)"]
```

(The sample app logs through its own `CustomLogger`, with a timestamp, colours and the arguments as a JSON array.)
The first `mvn` command takes a couple of seconds once the reactor has been built; `boost start` cannot be used for this
module because its framework versions are inherited from the reactor's parent POM, which the CLI's static project
check does not read (`Cannot determine the version of booster-framework-core declared in the pom.xml file`), so the
`java` command above is the CLI's launch done by hand.

* GraphQL over HTTP: `POST http://localhost:3000/graphql`. Health: `GET http://localhost:3000/sensor/health/`.
* GraphQL subscriptions: `ws://localhost:65529/websocket`, sub-protocol `graphql-ws` (the `subscriptions-transport-ws`
  messages `connection_init`, `start`, `stop`, `connection_terminate`). Set `LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT`
  to move it to another port.

In another terminal, the mutation and the queries of the end-to-end suites (`CartIT`, `QueriesIT`):

```console
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) { ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity }) }", "variables": {"cartId": "demo-cart", "productId": "demo-product", "quantity": 2}}'
{"data":{"ChangeCartItem":true}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "query CartReadModel($cartId: ID!) { CartReadModel(id: $cartId) { id cartItems { productId quantity } } }", "variables": {"cartId": "demo-cart"}}'
{"data":{"CartReadModel":{"id":"demo-cart","cartItems":[{"productId":"demo-product","quantity":2}]}}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "query CartTotalQuantity($cartId: ID!) { CartTotalQuantity(input: { cartId: $cartId }) }", "variables": {"cartId": "demo-cart"}}'
{"data":{"CartTotalQuantity":2}}
```

A mutation is answered after its events were stored, flushed and dispatched, so the read model can be queried at once.
A client subscribed over the websocket to `subscription { CartReadModel(id: "demo-cart") { id cartItems { productId
quantity } } }` receives a `data` message with the new read model after every change of that cart.

**Persistent data.** Everything lives in `framework-integration-tests/.booster/` (git-ignored), one JSON-lines file per
collection in the NeDB format of the TypeScript local provider, so a `.booster` directory written by the TypeScript
provider is served as well:

| File | Content |
| --- | --- |
| `events.json` | events and entity snapshots |
| `read_models.json` | read models (with a unique index on type, id and version) |
| `connections.json`, `subscriptions.json` | websocket connections and GraphQL subscriptions |
| `dispatch_journal.json` | Java only: stored events whose dispatch has not finished |
| `booster.lock` | Java only: locked by the running server, holds its process id |

**Stop and restart.** Ctrl-C (SIGINT) or SIGTERM stops the server gracefully: running requests and scheduled commands
finish, websocket connections are closed, and the log ends with `Stopping the local server on http://localhost:3000`
and `Local server stopped` (exit code 143 for SIGTERM). Start it again with the same command and the `CartReadModel`
query above returns the same cart. Delete `.booster` to start from an empty store.

**Crash recovery.** Every acknowledged write is on disk before the answer (`FileChannel.force`), and the events of one
command are written with a single append framed as an atomic batch (a `{"$$batch":<n>}` header line, which NeDB
ignores), so after `kill -9` nothing acknowledged is lost and no command is half applied. On the next start the
server, before serving:

1. locks `.booster/booster.lock`: a second server on the same project (another `boost start` on another port, an IDE
   run next to it) stops at once with `Another Booster process (pid N) is using the data files of ...` and exit
   code 1, instead of serving its own copy of the data;
2. loads every data file, discarding a torn last line or an incomplete last batch (whole, never a part of it) and
   skipping a few corrupt lines with a warning; with more than 10 % of corrupt lines it refuses to start (exit code 1,
   NeDB's `... Cautiously refusing to start NeDB to prevent dataloss.`) and leaves the file untouched;
3. deletes the websocket connections and subscriptions the dead process left (`Removed N websocket connection(s) and
   M subscription(s) left by a previous run ...`); clients reconnect and subscribe again;
4. re-dispatches, from the dispatch journal, the stored events whose dispatch had not started
   (`Re-dispatched N event(s) ...`; `No undispatched events to recover` when there are none). Events whose processing
   had already started are not processed twice (at most once, as on the cloud providers); their read models catch up
   with the entity's next event.

The durability suites (section 9) check all of this with `SIGKILL` under load, torn and corrupt files and repeated
crash cycles; [`framework-integration-tests/README.md`](framework-integration-tests/README.md) lists the guarantees.

## 5. Create your own application with the CLI

**Install the framework artifacts and the CLI.** Generated projects depend on `com.boostercloud:*:4.1.0`, so Maven
must find those artifacts:

```bash
cd java
mvn -B install -DskipTests                 # installs every module into ~/.m2 and builds cli/target/booster-cli-4.1.0.jar
export PATH="$PWD/cli/bin:$PATH"           # or: ln -s "$PWD/cli/bin/boost" /usr/local/bin/boost
boost --version                            # booster-cli/4.1.0 darwin-aarch64 java-27.0.1.0.3
```

To keep `~/.m2` untouched, install into a private repository and point every Maven run of the CLI at it (the CLI
passes its environment to Maven, and Maven 3.9+ reads `MAVEN_ARGS`). With an empty private repository the install
downloaded the third-party dependencies and took 34 s:

```bash
mvn -B install -DskipTests -Dmaven.repo.local="$HOME/.booster-repo"
export MAVEN_ARGS="-Dmaven.repo.local=$HOME/.booster-repo"
```

**Create the project and its resources** (anywhere outside `java/`):

```bash
boost new:project my-store --default       # no questions: version 0.1.0, MIT, Azure for "production", group com.example
cd my-store                                # pom.xml, Main.java, config/Config.java, a git repository with one commit
boost new:type CartItem --fields productId:UUID quantity:number
boost new:command ChangeCartItem --fields cartId:UUID productId:UUID quantity:number
boost new:event CartItemChanged --fields cartId:UUID productId:UUID quantity:number
boost new:entity Cart --fields 'cartItems:Array<CartItem>' --reduces CartItemChanged
boost new:read-model CartReadModel --fields 'cartItems:Array<CartItem>' --projects Cart:id
boost new:query CartTotalQuantity --fields cartId:UUID
```

Without `--default`, `new:project` asks for each value. The other generators are `new:event-handler <Name> -e <Event>`,
`new:scheduled-command <Name>`, `add:reducer --entity <Entity> --event <Event>`, `add:projection --read-model <ReadModel>
--entity <Entity:joinKey>` and `stub:publish` (to customise the templates); see [`cli/README.md`](cli/README.md).

The generated files leave placeholders to complete (as the TypeScript stubs do), for example
`@Command(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)`,
`register.events(/* YOUR EVENT HERE */)` and, in projections, `return ProjectionResult.of(/* NEW CartReadModel HERE */);`
(write `new CartReadModel(...)` in place of the comment). Completed, the application is (files under
`src/main/java/com/example/mystore/`; `common/CartItem.java` is generated complete):

```java
// commands/ChangeCartItem.java
package com.example.mystore.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.mystore.events.CartItemChanged;

@Command(authorize = All.class)
public record ChangeCartItem(
    UUID cartId,
    UUID productId,
    double quantity
) {
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}
```

```java
// events/CartItemChanged.java
package com.example.mystore.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;

@Event
public record CartItemChanged(
    UUID cartId,
    UUID productId,
    double quantity
) {
  public UUID entityID() {
    return cartId;
  }
}
```

```java
// entities/Cart.java
package com.example.mystore.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.mystore.common.CartItem;
import com.example.mystore.events.CartItemChanged;
import java.util.ArrayList;
import java.util.List;

@Entity
public record Cart(
    UUID id,
    List<CartItem> cartItems
) {

  @Reduces(CartItemChanged.class)
  public static Cart reduceCartItemChanged(CartItemChanged event, Cart currentCart) {
    List<CartItem> items = new ArrayList<>(currentCart == null ? List.of() : currentCart.cartItems());
    items.removeIf(item -> item.productId().equals(event.productId()));
    if (event.quantity() > 0) {
      items.add(new CartItem(event.productId(), event.quantity()));
    }
    return new Cart(event.cartId(), items);
  }
}
```

```java
// readmodels/CartReadModel.java
package com.example.mystore.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.mystore.common.CartItem;
import com.example.mystore.entities.Cart;
import java.util.List;

@ReadModel(authorize = All.class)
public record CartReadModel(
    UUID id,
    List<CartItem> cartItems
) {

  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<CartReadModel> projectCart(Cart entity, CartReadModel currentCartReadModel) {
    return ProjectionResult.of(new CartReadModel(entity.id(), entity.cartItems()));
  }
}
```

```java
// queries/CartTotalQuantity.java
package com.example.mystore.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.mystore.common.CartItem;
import com.example.mystore.entities.Cart;

@Query(authorize = All.class)
public record CartTotalQuantity(
    UUID cartId
) {
  public static double handle(CartTotalQuantity query, QueryInfo queryInfo) {
    return Booster.entity(Cart.class, query.cartId())
        .map(cart -> cart.cartItems().stream().mapToDouble(CartItem::quantity).sum())
        .orElse(0.0);
  }
}
```

**Build, start and try it:**

```console
$ boost build                              # mvn clean, then mvn package -DskipTests; errors go to ./errors.log
$ boost start -e local --port 3000         # builds again, runs the server until Ctrl-C; data in ./.booster
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "mutation { ChangeCartItem(input: { cartId: \"cart-1\", productId: \"book\", quantity: 2 }) }"}'
{"data":{"ChangeCartItem":true}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "mutation { ChangeCartItem(input: { cartId: \"cart-1\", productId: \"pen\", quantity: 3 }) }"}'
{"data":{"ChangeCartItem":true}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "{ CartReadModel(id: \"cart-1\") { id cartItems { productId quantity } } }"}'
{"data":{"CartReadModel":{"id":"cart-1","cartItems":[{"productId":"book","quantity":2},{"productId":"pen","quantity":3}]}}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "{ CartTotalQuantity(input: { cartId: \"cart-1\" }) }"}'
{"data":{"CartTotalQuantity":5}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "{ ListCartReadModels(filter: { id: { eq: \"cart-1\" } }) { items { id cartItems { productId quantity } } } }"}'
{"data":{"ListCartReadModels":{"items":[{"id":"cart-1","cartItems":[{"productId":"book","quantity":2},{"productId":"pen","quantity":3}]}]}}}
```

After Ctrl-C and another `boost start -e local --port 3000`, the same queries return the same cart. The environment
can also come from `BOOSTER_ENV`; the full API (notifications, event handlers, scheduled commands, roles, `before`
hooks, migrations, reading from code with `Booster.readModel(...)`) is in [`ARCHITECTURE.md`](ARCHITECTURE.md) §6b,
and the sample application uses all of it.

## 6. Azure

Implemented: the runtime provider (`framework-provider-azure`: events, snapshots, read models, subscriptions and
connections in Cosmos DB, notifications through Web PubSub, event streaming through Event Hubs, one entry point per
Azure Function) and the deploy-time provider (`framework-provider-azure-infrastructure`: the Terraform configuration
of every resource the TypeScript provider creates, function packages for the Java worker, `deploy` and `nuke`, rocket
hooks). An environment uses it with `config.providerPackage = "com.boostercloud.framework.provider.azure"` and the
dependencies `booster-framework-provider-azure` and `booster-framework-provider-azure-infrastructure` (a project created
with the Azure provider, like `my-store` above, has both and a `production` environment).

```bash
export REGION="East US" AZURE_SUBSCRIPTION_ID=<subscription id>
boost synth -e production       # cdktf.out/stacks/my-storeproduction/cdk.tf.json, functionApp.zip, baseWebPubSubBinding.zip
export AZURE_APP_ID=<client id> AZURE_SECRET=<client secret> AZURE_TENANT_ID=<tenant id>
boost deploy -e production      # synth, terraform init/plan/apply in the stack directory, Kudu zip deploys, Web PubSub hub
                                # (refused by default today: see "Known blocker" below)
boost nuke -e production        # asks for the application name (-f skips it) and deletes the resource group
```

* `synth` needs only `REGION` and `AZURE_SUBSCRIPTION_ID` and contacts no Azure service (it took 4 s for `my-store`).
  To check its output with Terraform: `cd cdktf.out/stacks/my-storeproduction && terraform init -backend=false &&
  terraform validate` (`Success! The configuration is valid.`).
* `deploy` and `nuke` also need the service principal (`AZURE_APP_ID`, `AZURE_SECRET`, `AZURE_TENANT_ID`); `terraform` must be on the `PATH` (or set
  `BOOSTER_TERRAFORM_BINARY`, e.g. `tofu`) and authenticated by its own means (`ARM_*` variables or `az login`), as with
  the TypeScript provider.
* Optional: `BOOSTER_AZURE_SERVICE_PLAN_BASIC=true` (B1 plan), `BOOSTER_USE_WAF=true`, `AZURE_MAX_CONTAINER_THROUGHPUT`,
  `AZURE_MAX_DATABASE_THROUGHPUT`, `BOOSTER_AZURE_JAVA_VERSION` (default `21`), `BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK`
  (see the known blocker below), `BOOSTER_APP_CLASS` (default: the main class). The function apps' app settings are
  listed in
  [`framework-provider-azure-infrastructure/README.md`](framework-provider-azure-infrastructure/README.md).
* With the local provider, `boost deploy`, `synth` and `nuke` fail with the TypeScript message (`... with a provider
  that does not support this feature ...`) and exit code 1.

What the contract tests verify, without deploying anything (`mvn -B verify`, plus the `azurecontract` suites of
`-Pe2e`): the exact REST requests of Cosmos DB, Event Hubs, Web PubSub, Resource Manager, Entra ID and Kudu against
in-JVM fake services, with their signatures re-derived independently and against OpenSSL known answers; the whole
provider library against the fakes; the synthesized Terraform of four configurations against golden files and, for
the sample app, against the names and resources the TypeScript code derives; the `function.json` of every function and
the package layouts; the `deploy`/`nuke` command and request sequence against a stateful fake subscription; the
function entry points of a mutation, its event, the stream, the projection, a scheduled command and a subscription
notification against fake Cosmos DB, Event Hubs and Web PubSub.

The opt-in Terraform check runs `terraform init -backend=false` and `terraform validate` on synthesized stacks (it
needs `terraform` and downloads the azurerm 5.x provider once; `TF_PLUGIN_CACHE_DIR` reuses it; with the provider
cached the two commands took 23 s and 42 s). The three offline Azure suites of the sample app alone
(`'-Dit.test=AzureSynthesisContractIT,AzureDeployNukeContractIT,AzureRuntimeContractIT'`, 49 tests) take 6 s.

```bash
BOOSTER_TERRAFORM_VALIDATE=true mvn -B -Pe2e -pl framework-integration-tests -am verify -Dtest=NONE \
    -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AzureTerraformValidateIT          # the sample app's stack
BOOSTER_TERRAFORM_VALIDATE=true mvn -B -pl framework-provider-azure-infrastructure -am verify \
    -Dtest=TerraformValidateTest -Dsurefire.failIfNoSpecifiedTests=false   # 3 synthesized stacks + every golden stack
```

The second command validates three synthesized stacks (one with test rockets, WAF and the basic plan) and every golden
stack of the infrastructure module and of both rockets (`rockets/*/src/test/resources/golden`), 9 in all. Offline,
point `TF_CLI_CONFIG_FILE` at a `provider_installation { filesystem_mirror { … } }` configuration whose path is a
plugin cache directory.

**Known blocker: Java 27 on Azure Functions.** The framework and the applications `boost new` generates are compiled
for Java 27 (class file version 71), while the function apps are created with `java_version =
BOOSTER_AZURE_JAVA_VERSION` (default `21`; the azurerm 5.x provider accepts `1.8`, `11`, `17` and `21`), and a Java 21
worker cannot load those classes. This is decided offline,
not left to a deployment to find out: `boost deploy` checks the packaged class files against `java_version` before
building or creating anything and refuses with an error naming the first class the runtime cannot load; `boost synth`
only logs that as a warning, so stacks and packages can still be inspected. `BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK=true`
skips the check, for function apps that run a JVM shipped by the application (selected with the
`languageWorkers__java__defaultExecutablePath` app setting; not verified). Running the port on Azure therefore needs a
Java stack Azure does not offer yet, such a custom JVM, or building the framework and the application for
`--release 21`, which [`ARCHITECTURE.md`](ARCHITECTURE.md) §2 does not allow today. The details are in
[`framework-provider-azure-infrastructure/README.md`](framework-provider-azure-infrastructure/README.md) ("Known
blocker").

What cannot be verified without deploying: that Azure accepts the deployment (`terraform apply`), the real Kudu,
Functions host, Java worker bindings and Web PubSub extension, and the live Cosmos DB, Event Hubs and Blob Storage
service conventions.

## 7. Rockets

Both rockets are one Maven artifact each with their local and Azure parts; add the dependency and register the rocket
in the environment's configuration. This is the `local` environment of `my-store` above with both (the handler and the
read model are new files of the project):

```xml
<dependency>
  <groupId>com.boostercloud</groupId>
  <artifactId>booster-rocket-webhook</artifactId>
  <version>${booster.version}</version>
</dependency>
<dependency>
  <groupId>com.boostercloud</groupId>
  <artifactId>booster-rocket-file-uploads</artifactId>
  <version>${booster.version}</version>
</dependency>
```

```java
// config/Config.java (imports: BoosterWebhook, WebhookParamsEvent, BoosterRocketFiles, RocketFilesUserConfiguration, HelloHandler)
Booster.configure("local", config -> {
  config.appName = "my-store";
  config.providerPackage = "com.boostercloud.framework.provider.local";
  config.rockets = List.of(
      new BoosterWebhook(config, List.of(
          WebhookParamsEvent.builder().route("hello").handlerClass(HelloHandler.class).build())).rocketForLocal(),
      new BoosterRocketFiles(config, List.of(
          new RocketFilesUserConfiguration("files", "uploads", List.of("photos")))).rocketForLocal());
});

// webhooks/HelloHandler.java
public class HelloHandler {
  public static WebhookHandlerReturnType handle(WebhookEvent event, Register register) {
    Object name = event.body() instanceof Map<?, ?> body ? body.get("name") : null;
    return WebhookHandlerReturnType.json(Map.of("greeting", "Hello, " + name));
  }
}

// commands/FileUploadPut.java
@Command(authorize = All.class)
public record FileUploadPut(String directory, String fileName) {
  public static String handle(FileUploadPut command, Register register) {
    return new FileHandler(Booster.config()).presignedPut(command.directory(), command.fileName());
  }
}

// readmodels/UploadedFileReadModel.java: one row per uploaded file
@ReadModel(authorize = All.class)
public record UploadedFileReadModel(UUID id, Object metadata) {
  @Projects(entity = UploadedFileEntity.class, joinKey = "id")
  public static ProjectionResult<UploadedFileReadModel> project(UploadedFileEntity entity, UploadedFileReadModel current) {
    return ProjectionResult.of(new UploadedFileReadModel(entity.id(), entity.metadata()));
  }
}
```

```console
$ curl -s -X POST http://localhost:3000/hello -H 'Content-Type: application/json' -d '{"name":"Booster"}'
{"greeting":"Hello, Booster"}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "mutation { FileUploadPut(input: { directory: \"photos\", fileName: \"cat.txt\" }) }"}'
{"data":{"FileUploadPut":"http://localhost:3000/files/uploads/photos/cat.txt"}}
$ echo meow > cat.txt
$ curl -s -X PUT -H 'Content-Type: application/octet-stream' --data-binary @cat.txt http://localhost:3000/files/uploads/photos/cat.txt
{"status":"success","result":{"message":"File uploaded","data":{"name":"cat.txt","mimeType":"unknown","size":0}}}
$ curl -s http://localhost:3000/graphql -H 'Content-Type: application/json' -d '{"query": "{ ListUploadedFileReadModels(filter: {}) { items { id metadata } } }"}'
{"data":{"ListUploadedFileReadModels":{"items":[{"id":"<md5 of the metadata>","metadata":{"BOOSTER_ROCKET_FUNCTION_ID":"rocket-files","uri":"http://localhost:3000/files/uploads/photos/cat.txt","name":"photos/cat.txt"}}]}}}
```

The file is stored in `files/uploads/photos/cat.txt` under the project directory, and a `GET` of the presigned URL
downloads it. The server notifies the upload itself before answering the `PUT`, so the read model appears moments
later (also when the server stops right after the upload); files copied into the folder by hand reach it through the
file watcher (on macOS the JDK polls every 2 s, so query it a few seconds later). For Azure use `rocketForAzure()` in the Azure environment: the webhook rocket adds a
function app and gateway rules, the file-uploads rocket storage accounts, containers, a function app with blob
triggers and role assignments. Every option, the request and response shapes and the error statuses are in
[`rockets/rocket-webhook/README.md`](rockets/rocket-webhook/README.md) and
[`rockets/rocket-file-uploads/README.md`](rockets/rocket-file-uploads/README.md).

The classes used above are `com.boostercloud.rocket.webhook.core.BoosterWebhook`,
`com.boostercloud.rocket.webhook.types.{WebhookParamsEvent, WebhookEvent, WebhookHandlerReturnType}`,
`com.boostercloud.rocket.fileuploads.core.{BoosterRocketFiles, FileHandler}` and
`com.boostercloud.rocket.fileuploads.types.{RocketFilesUserConfiguration, UploadedFileEntity}`; the rest are the
framework classes of section 5.

## 8. Module map and architecture

All modules have the group `com.boostercloud` and the version `4.1.0` (the TypeScript version they port).

| Maven module (`java/...`, artifact) | TypeScript package | Purpose |
| --- | --- | --- |
| `metadata-booster` (`booster-metadata`) | `metadata-booster` | class, property and type metadata in the shape of the TypeScript transformer |
| `framework-types` (`booster-framework-types`) | `framework-types` | concepts, `BoosterConfig`, envelopes, provider SPI, errors, the shared JSON mapper |
| `framework-common-helpers` (`booster-framework-common-helpers`) | `framework-common-helpers` | instance binding, retries, promises on virtual threads, loggers, rocket loader |
| `framework-core` (`booster-framework-core`) | `framework-core` | annotations, `Booster`, dispatchers, event pipeline, read models, GraphQL, auth, migrations, health |
| `framework-provider-local` (`booster-framework-provider-local`) | `framework-provider-local` | local runtime: NeDB-format file store under `.booster/`, WebSocket server, crash recovery |
| `framework-provider-local-infrastructure` (`booster-framework-provider-local-infrastructure`) | `framework-provider-local-infrastructure` | local HTTP server (Express counterpart), scheduler, rocket mounting, test helper |
| `framework-provider-azure` (`booster-framework-provider-azure`) | `framework-provider-azure` | Azure runtime: Cosmos DB, Event Hubs, Web PubSub (REST clients), Azure Functions entry points |
| `framework-provider-azure-infrastructure` (`booster-framework-provider-azure-infrastructure`) | `framework-provider-azure-infrastructure` | Terraform synthesis, function packages, deploy and nuke, test helper |
| `application-tester` (`booster-application-tester`) | `application-tester` | GraphQL HTTP/WebSocket clients, test tokens, counters and queries for tests |
| `rockets/rocket-webhook` (`booster-rocket-webhook`) | `rocket-webhook-*` (external repository) | HTTP webhooks for local and Azure |
| `rockets/rocket-file-uploads` (`booster-rocket-file-uploads`) | `rocket-file-uploads-*` (external repository) | presigned uploads/downloads, listing and deletion, upload events, local and Azure |
| `cli` (`booster-cli`) | `cli` | the `boost` command (`cli/bin/boost`) |
| `cli-integration-tests` (`booster-cli-integration-tests`) | `framework-integration-tests/integration/provider-unaware/cli` | end-to-end tests of the real `boost` launcher (test code only) |
| `framework-integration-tests` (`booster-framework-integration-tests`) | `framework-integration-tests` | the sample application and its end-to-end, durability and Azure contract suites |

Where to read more:

* [`ARCHITECTURE.md`](ARCHITECTURE.md): the binding design contract (TypeScript to Java mappings, the user-facing API,
  JSON rules, asynchrony, start-up, persistence, testing).
* Each module's `README.md`: its API, its tests, and a **Differences from TypeScript** section.
* Parity ledgers, item by item against the TypeScript sources and tests, with the Java test that pins each behaviour:
  [`framework-core/PARITY.md`](framework-core/PARITY.md), [`framework-provider-local/PARITY.md`](framework-provider-local/PARITY.md)
  (local provider and its infrastructure), [`framework-provider-azure/PARITY.md`](framework-provider-azure/PARITY.md)
  (both Azure modules), [`rockets/rocket-webhook/PARITY.md`](rockets/rocket-webhook/PARITY.md),
  [`rockets/rocket-file-uploads/PARITY.md`](rockets/rocket-file-uploads/PARITY.md).

## 9. Testing strategy and what was tested

| Layer | Where | What it gives |
| --- | --- | --- |
| Unit tests | every module, `*Test` (surefire) | ports of the TypeScript unit tests, one Java class per `*.test.ts` file (JUnit 6, AssertJ; Mockito replaces sinon), plus tests of what TypeScript leaves untested |
| In-JVM integration | `framework-core` `integration/*IntegrationTest` | the real core with nothing mocked on a complete in-memory provider (`InMemoryProvider`): GraphQL over HTTP and WebSocket, authorization with real RS256 tokens, migrations, events API, health, error hooks, tracing, concurrency |
| Differential tests | `framework-provider-local`, both rockets, `application-tester` | expectations generated by the real JavaScript implementations: NeDB 4.0.2 queries, cursors, modifiers and data files (`NedbDifferentialTest`, 996 cases), V8 regular expressions (`JsRegExpDifferentialTest`, 1500 patterns), minimatch and brace-expansion (about 10 500 cases), Busboy's own test vectors, Node's WHATWG URL parser, a JWT signed by `jsonwebtoken` |
| Stress and fault injection | `framework-provider-local` | concurrent writers on virtual threads, a writer JVM killed with `SIGKILL` mid-write, truncation at every offset (of single lines and of atomic batches), injected write and truncate failures and recorded flushes, deterministic interleavings of recovery and live dispatches, 100k-document collections |
| Azure contract tests | `framework-provider-azure`, `framework-provider-azure-infrastructure`, both rockets | the production REST clients against in-JVM fake services (requests, signatures recomputed independently and against OpenSSL known answers, paging, retries, error mapping), golden Terraform files, function definitions and packages, the deploy/nuke command and HTTP sequences |
| End-to-end | `framework-integration-tests`, `*IT` (failsafe, `-Pe2e`) | the sample app launched as a real JVM exactly as the CLI launches it, driven over HTTP and WebSocket by the ported TypeScript end-to-end and functionality suites |
| Durability and recovery | `framework-integration-tests` `providerspecific.local.durability` | graceful restarts, `SIGKILL` under load, repeated crash cycles, torn and corrupt data files, TypeScript-written `.booster` files, scheduled commands across restarts (and, in `framework-provider-local-infrastructure`'s `RestartTest`, a second server on the same data files and the connections a crash left) |
| Azure end to end, offline | `framework-integration-tests` `azurecontract` | `synth` of the sample app compared with the TypeScript synthesis, `deploy`/`nuke` against a stateful fake subscription, the function entry points against fake Cosmos DB / Event Hubs / Web PubSub |
| CLI | `cli` (unit), `cli-integration-tests` (`-Pe2e`) | the real `boost` launcher: `new:project`, every generator compared byte for byte with fixtures and compiled, `build`, `clean`, `start` with a mutation, a query and a restart, `deploy`/`synth`/`nuke`, rocket commands |

Test counts of the final phase-4 runs on 2026-11-11 (all fix branches merged, all green; `mvn -B verify` 5 min 21 s,
then `mvn -B -Pe2e verify` twice with identical counts, 11 min 13 s and 10 min 30 s):

| Module | Unit and in-JVM tests (`mvn -B verify`) | End-to-end tests (`-Pe2e` only) |
| --- | ---: | ---: |
| `metadata-booster` | 44 | |
| `framework-types` | 148 | |
| `framework-common-helpers` | 84 | |
| `framework-core` | 944 | |
| `framework-provider-local` | 3 071 | |
| `framework-provider-local-infrastructure` | 179 | |
| `framework-provider-azure` | 272 | |
| `framework-provider-azure-infrastructure` | 185 (4 skipped: the opt-in Terraform validation) | |
| `application-tester` | 124 | |
| `rockets/rocket-webhook` | 406 | |
| `rockets/rocket-file-uploads` | 220 | |
| `cli` | 530 | |
| `cli-integration-tests` | | 83 |
| `framework-integration-tests` | 99 | 368 (11 skipped) |
| **Total** | **6 306** | **451** |

The 11 skipped end-to-end tests are the ones TypeScript itself disables (`LocalAuthIT`, the local `commands` and
`read-models` suites), the assertions only a real Azure deployment can satisfy (each with a local replacement test),
the TypeScript readonly-array schema expectations (with a Java replacement test) and the opt-in
`AzureTerraformValidateIT`. Counts are JUnit test invocations: parameterised and differential tests count each case
(for example, 1 500 of the local provider's tests are the V8 regular-expression cases).

## 10. Differences from TypeScript and known limitations

Consolidated from the "Differences from TypeScript" sections of the module READMEs and from the parity ledgers, which
have the details and the tests. Observable behaviour (GraphQL schema and names, payloads, error messages and codes,
stored document shapes, resource names, ordering and retry rules) is kept; the differences below are the deliberate
ones, each with the reason.

**Writing applications**

* Decorators are annotations, applied by a package scan in `Booster.start(Main.class)` / `Booster.run`: Java has no
  decorators and no import-time side effects. For the same reason configuration blocks live in `BoosterConfigurator`
  classes.
* Function-valued options (`before` hooks, custom authorizers, join-key functions) are classes with a no-argument
  constructor: annotation attributes can only hold constants and classes. Handlers, reducers and projections stay
  static methods, as in TypeScript, found by name with JavaScript-like arity (fewer parameters are allowed).
* Optional properties are `Optional<T>` or `@Nullable` (TypeScript `?`), and registration validates the handler
  signatures that TypeScript's type checker enforces: Java has no `?` and no compile-time check of annotation targets.
* `Promise`s are blocking calls on virtual threads, so handlers, reducers of different entities and projections can
  really run in parallel: state they share must be thread-safe (JavaScript interleaves on one thread).
* `createInstance` binds recursively into typed records and classes and drops unknown keys (TypeScript's
  `Object.assign` is shallow and keeps them): Java needs typed objects.
* A number with a fraction (or `NaN`/infinite) bound to a whole-number property (`int`, `long`, `short`, `byte`,
  their boxes, `OptionalInt`/`OptionalLong`, `BigInteger`) fails with `Cannot convert 2.5 to int: the number is not
  an integer` (a mutation input answers with that error); TypeScript has one number type and keeps `2.5`, and Jackson
  alone would truncate it silently. Whole doubles such as `2.0` are accepted. The rule also applies when stored
  documents are bound to events, entities and read models: a stored `2.5` in an `int` field fails to bind instead of
  being truncated.
* `Booster.readModel(X.class).select(...)` is read with `searchSelected()`, `searchOneSelected()` or
  `searchSelectedPaginated()` (plain maps of the selected paths) or `searchRaw()`; the typed `search()`, `searchOne()`
  and `searchPaginated()` throw an `IllegalStateException` naming the right method when a selection is set.
  TypeScript retypes the builder to `Partial<T>`, which Java cannot do.
* Where TypeScript throws a plain `Error`, Java throws `IllegalStateException`/`IllegalArgumentException` with the same
  message, so HTTP error titles read `Illegal State Exception` instead of `Error`: there is no JavaScript `Error`
  class to reuse. Error names used for retry matching are simple class names (Booster errors never set `error.name`).

**GraphQL**

* graphql-java replaces graphql-js: syntax, validation and coercion error texts are graphql-java's (resolver errors
  keep the thrown message); there is no graphql-js on the JVM.
* Documents are validated with the specification rules only, like graphql-js (graphql-java's "good faith
  introspection" and complexity limits are off), so the TypeScript introspection documents work.
* `String` lists are cached by name in registration order, which is class-path order in Java and import order in
  TypeScript, so the sample app's string lists are `[String!]` where TypeScript has `[String]`; Java has no
  `ReadonlyArray` (a read-only array filter is `StringArrayPropertyFilter`, not `JSON`).
* Classes that refer to themselves are supported (TypeScript overflows the stack); a `void` handler maps to `JSON!` and
  answers `true`, as the current TypeScript code does.
* The `Date` scalar also accepts ISO strings and keeps them as normalised ISO strings (local date-times are read as
  UTC); `JSON` is Booster's own scalar (graphql-scalars is not available).
* Security fixes, each a deliberate divergence from TypeScript:
  * Subscription filters are evaluated whole: every top-level property, operator and nested property must match,
    `and`/`or`/`not` combine filters and `isDefined` is implemented. TypeScript decides on the first property only, so
    `{id: {ne: ""}, userId: {eq: <me>}}` passed a `before` hook that checks `userId` and still delivered every user's
    changes over the websocket. A subscriber's `regex`/`iRegex` evaluation is bounded (`max(10⁸, 4·n²)` characters
    read, at most 10⁹) and fails with `The regular expression /…/ is too complex to evaluate ...` instead of pinning
    the thread.
  * Websocket identities expire: a `start` on a connection past its `expirationTime` or its token's `exp` deletes the
    connection and answers `connection_error` (the client sends a new `connection_init` on the same socket), and the
    notifier skips and deletes subscriptions past theirs. TypeScript checks the token once at `connection_init`.
  * With `enableGraphQLIntrospection = false`, `__type` selections are refused like `__schema` ones (aliases and
    fragments included); `__typename` stays available. TypeScript only checks the operation name and `__schema`.

**Local provider and server**

* Every acknowledged write is flushed (`FileChannel.force`): about 4 ms per write and roughly 250 single-document
  writes per second per collection on a laptop. NeDB never flushes, which loses data on a crash.
* Java-only durability: the events of one command (and every other write of several lines) are stored with one
  append framed by a `{"$$batch":<n>}` header line, which NeDB ignores, so a crash that cuts the append at any byte
  leaves all of them or none; a dispatch journal re-dispatches events a crash left undispatched, a torn last line or
  batch is discarded on start, and more than 10 % of corrupt lines stop the start (NeDB's message) instead of failing
  the first request. Events whose processing had started are not processed twice (core's at-most-once rule), so a
  read model may miss such an event until the entity's next event. That a flushed write survives a power loss
  depends on the device and is not tested (write failures and flushes are, with injected faults).
* One process per project: the server locks `.booster/booster.lock`, and a second one on the same files does not
  start (TypeScript lets two processes share the files, each serving only its own writes).
* `createdAt` is taken under the events write lock and is strictly increasing within the process (the current time,
  or 1 ms after the previous event), so its order is the order in which events become visible to snapshots; under a
  burst of more than one event per millisecond it runs a few milliseconds ahead of the clock (TypeScript gives events
  of the same millisecond the same `createdAt`).
* Regular expressions from filters (`regex`, `iRegex` and the unescaped sources of `contains`/`beginsWith`/`includes`)
  are evaluated with a budget of `max(10⁸, 4·n²)` characters read over a value of `n` characters (at most 10⁹);
  beyond it the search fails with `The regular expression /…/ is too complex to evaluate ...` (security: a pattern
  such as `^(.*a){20}$` backtracks for an exponential time on a short value; TypeScript has no limit).
* Fixed TypeScript bugs: connection data is found by `connectionID`, GraphQL `stop` deletes the subscription, a race
  that stored two documents for one new read model, frames received before `CONNECT` finished are no longer lost, and
  the connections and subscriptions a crashed process left are deleted at start-up (TypeScript keeps them for ever and
  runs those subscriptions on every change).
* Generated `_id`s are monotonic, so ties (same-millisecond events) come in insertion order instead of random order.
* Regular expressions in filters keep JavaScript syntax (translated to Java patterns, pinned against V8); not emulated:
  JavaScript's non-Unicode case folding of a few characters, code-unit matching outside the BMP, back references to
  groups that did not participate.
* `start` blocks and stops gracefully on SIGINT/SIGTERM (exit code 143); a taken port fails the start (Node crashes
  with `EADDRINUSE`); `deploy`, `synth` and `nuke` on the local provider fail with the CLI's message.
* A scheduled command with no schedule field is never run (the TypeScript local provider runs it every minute, the
  documentation and Azure do not); runs that fell while the process was down are not replayed (it is a cron).
* The HTTP layer is a small re-implementation of the Express 4 features the framework and the rockets use: no
  `X-Powered-By`, `ETag`/`304`, `Range`, `res.sendFile`, cookies or static files.
* Kept on purpose (TypeScript behaviour): GraphQL and health successes always answer `200` without the result headers,
  offset cursors (`(limit ?? 1) + offset`), only the first operator of an operator object translated, only `"ASC"`
  sorting ascending, the event search returning soft-deleted events.

**Azure**

* Cosmos DB, Event Hubs, Web PubSub and Resource Manager are reached through small `java.net.http` REST clients instead
  of the Azure SDKs (excluded by the architecture); cross-partition non-`VALUE` aggregates and `GROUP BY` are rejected
  (Booster issues neither). Event Hubs uses the REST "send batch" API instead of AMQP, with batches of at most
  256 KiB, the publication limit of the Basic namespace the infrastructure deploys.
* Cosmos DB follows the SDK's single-region retry policies (429 for every request; connection failures, timeouts, 503
  and 408 for reads and queries only). Where it differs: a partition split or move (410 with substatus 1002, 1007 or
  1008) during a cross-partition query lists the ranges again and re-runs the query (up to 3 times) where the SDK
  repairs the affected range only; a page of `p` rows after `n` rows of a paged cross-partition `ORDER BY` query
  reads up to `n + p + 1` rows of each range where the SDK resumes each range from its own continuation (about `p`);
  there is no session-token retry policy (the client sends no session tokens).
* Filter property names are written into the Cosmos DB SQL as escaped string literals and sanitised placeholder names
  (security): TypeScript pastes the keys of a `JSON`-typed filter, which the GraphQL client chooses, into the query
  text, so a key such as `x"]) OR (1=1) OR IS_DEFINED(c["x` returned every document. Identifier-like keys give exactly
  the TypeScript SQL.
* Terraform JSON is emitted by the port itself (no cdktn, no Node) and `deploy` runs `terraform init/plan/apply`; the
  key order and the `"//"` metadata of cdktn could not be compared byte for byte.
* Function apps use the Java worker: one `function.json` per function instead of a generated `functions.js`, a Java
  stack instead of `node_version`, and the new app setting `BOOSTER_APP_CLASS`.
* `rawToEnvelopes` drops snapshot documents (Java envelopes have a constant `kind`); the original documents are kept
  aside so that streaming and deletion send exactly what TypeScript sends.
* **Known blocker: Java 27 on Azure Functions.** The framework is compiled for Java 27 and the function apps run Java
  `BOOSTER_AZURE_JAVA_VERSION` (default `21`), which cannot load it, so `boost deploy` refuses before creating anything
  and names the first class the runtime cannot load (`synth` only warns; `BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK=true`
  skips the check for a custom JVM). TypeScript has no counterpart: Node runs any JavaScript it deploys. See section 6
  for the ways out. Also not verifiable without deploying: `terraform apply`, Kudu, the Functions host and the Java
  worker's bindings (Web PubSub return value, binding data types), the Web PubSub extension and the live Cosmos DB,
  Event Hubs and Blob Storage conventions (for example, whether Blob Storage would refuse a one-digit day in
  `x-ms-date`; the port sends the SDK's two-digit form).

**CLI**

* Projects are Maven projects with Java packages (`readmodels`, `eventhandlers`, `scheduledcommands`: no hyphens);
  `--default` and the first provider choice are Azure (AWS is not ported); a new `-g/--groupId` sets the Maven group and
  the root package.
* The CLI never loads user code: it builds with Maven and launches the application (`java -cp target/classes:target/lib/*
  <main> <action>`), so environment and `appName` checks are static, and there is no `.deploy` sandbox (Maven already
  separates the runtime class path).
* Prompts read numbered answers from standard input (inquirer uses arrow keys); parse errors (picocli) exit with code 2.
* Projection stubs (`new:read-model --projects`, `add:projection`) generate
  `return ProjectionResult.of(/* NEW X HERE */);`: TypeScript's `ProjectionResult<T>` is `T | ReadModelAction`, so a TypeScript projection may return the read
  model itself, while Java needs the wrapper. Writing `new X(...)` in place of the comment compiles in both.
* The generated `.gitignore` anchors `/target/`, `/tmp/` and `/coverage` to the project root (TypeScript leaves `tmp/`
  and `coverage` unanchored): Java packages are directories, so a package named `target`, `tmp` or `coverage`, or the
  group `com.target`, would otherwise be left out of git.
* On a Java older than 27 the CLI stops with `boost: Java 27 or newer is required, but found Java 21 at ...` (exit
  code 1) instead of an `UnsupportedClassVersionError`: the jar's entry point is compiled for Java 8 and checks the
  version first. TypeScript's `bin/run` does not check the Node.js version.

**Rockets**

* Webhook: Java error titles (see above); a malformed multipart body answers `500` with Busboy's message (TypeScript
  never answers); Busboy's urlencoded parser (only reachable with a contrived content type) is not ported because no
  verified copy of it was available to pin against.
* File uploads: `deleteFile` exists for both providers (TypeScript documents it as a TODO), and its file name must be
  a plain name of the checked directory (not empty, `.` or `..`, no `/` or `\`), otherwise `Invalid file name
  <fileName>` (security: `../admin/secret.txt` deleted a file of a folder the patterns do not allow; the presigned
  URLs keep TypeScript's unvalidated file names); a path outside the container is refused with `400` (TypeScript
  reads or writes any file); the Azure credential chain is environment, managed identity and Azure CLI only; the Java
  blob trigger passes only `blobTrigger` and `uri` as metadata, so entity ids (an md5 of the metadata) differ from a
  Node deployment.
* File uploads, local watcher: the server notifies each upload itself before answering the `PUT`, and a stop waits
  for the notifications in flight, so an acknowledged upload always gets its `UploadedFileEvent`, across a restart
  too; the watch event still follows, so each upload is notified once more (TypeScript does not debounce either).
  Other changes (files copied by hand, `deleteFile`) come from the JDK `WatchService`, which polls every 2 s on macOS:
  they arrive later than with Node, and those not yet reported when the server stops are not notified (a start
  notifies nothing, as in TypeScript).

**Tests and tooling**

* The end-to-end harness uses free ports and readiness probes instead of `3000`/`65529` and fixed sleeps. TypeScript
  assertions that pass silently (`try`/`catch` without `fail`) are strict here, and the few assertions only Azure can
  satisfy (health `503`, cursor `'100'`, `Resource Not Found`) are skipped on the local provider, where an extra test
  pins the local answer.
* Not ported, with their reason in the module READMEs: AWS (provider, environments, gates, rockets), the Artillery
  load suites (AWS only and disabled in TypeScript), the Effect-TS module of the TypeScript CLI, and the suites
  TypeScript itself disables (`LocalAuthIT`, the local `commands`/`read-models` suites).
* Verified only partly: `boost.cmd` was run under Wine (no Windows `cmd.exe` was available), the `$$batch` framing was
  not re-read by a real `@seald-io/nedb` (not in the local npm cache; NeDB skips lines without `_id` by its own rule),
  and the opt-in Terraform validation needs `terraform` and the azurerm provider.
