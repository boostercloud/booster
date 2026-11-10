package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Sleep;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.Boost.RunningBoost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.CliTestEnvironment;
import com.boostercloud.cli.integration.helper.FileHelper;
import com.boostercloud.cli.integration.helper.JavaSources;
import com.boostercloud.cli.integration.helper.LocalApplication;
import com.boostercloud.cli.integration.helper.Ports;
import com.boostercloud.cli.integration.helper.Processes;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Java only (the TypeScript CLI suite never runs an application): the life of a project created by the CLI.
 *
 * <ol>
 *   <li>{@code boost new:project} creates a local-only project, resolving its dependencies (from the isolated
 *       repository) and initialising git;</li>
 *   <li>the generators add an event, a type, an entity with a reducer, a command and a read model with a projection,
 *       whose placeholders are completed as a user would;</li>
 *   <li>{@code boost start --verbose -e local --port <free port>} (as {@code start} of {@code cli-helper.ts}, plus a
 *       free websocket port) builds and runs it; a mutation and a read-model query round-trip through GraphQL with
 *       the application tester;</li>
 *   <li>the CLI is stopped with SIGTERM ({@code process.kill(pid)} of {@code local/stop/setup.ts}), which it forwards
 *       to the application;</li>
 *   <li>a second {@code boost start} serves the data stored by the first one, and the entity keeps its state.</li>
 * </ol>
 */
@DisplayName("Start (Java)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "stops the CLI with SIGTERM")
class CliStartIT {
  static final String PROJECT_NAME = "cart-demo-start";
  static final String BASE_PACKAGE = "com.example.cartdemostart";
  static final Duration START_TIMEOUT = Duration.ofMinutes(5);

  static final String CHANGE_CART_ITEM =
      """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }""";

  static final String CART_READ_MODEL =
      """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          items {
            productId
            quantity
          }
        }
      }""";

  final Path sandbox = FileHelper.sandboxPathFor("start");
  final Path project = sandbox.resolve(PROJECT_NAME);
  final String cartId = UUID.randomUUID().toString();
  final String firstProductId = UUID.randomUUID().toString();
  final String secondProductId = UUID.randomUUID().toString();

  RunningBoost server;
  int httpPort;

  @BeforeAll
  void createSandbox() {
    FileHelper.removeFolders(List.of(sandbox));
    FileHelper.createFolder(sandbox);
  }

  @AfterAll
  void stopEverything() {
    if (server != null) {
      server.close();
    }
    Processes.killAll();
    Sandbox.removeSandboxProject(sandbox);
  }

  @Test
  @Order(1)
  @DisplayName("new:project creates a local project, installing its dependencies and initialising git")
  void createsTheProject() {
    CliResult result =
        Boost.run(
            sandbox,
            List.of(
                "new:project", PROJECT_NAME,
                "--description", "A cart",
                "--version", "1.0.0",
                "--author", "The Agile Monkeys",
                "--homepage", "https://www.boosterframework.com/",
                "--license", "Apache",
                "--repository", "https://github.com/boostercloud/booster/",
                "--providerPackageName", "com.boostercloud.framework.provider.local",
                "--groupId", "com.example"));

    assertSucceeds(result, "boost new", "Creating project root", "Generating config files", "Installing dependencies",
        "Initializing git repository", "Project generated");
    assertThat(result.stdout()).doesNotContain("Skipping");
    assertThat(project.resolve(".git")).isDirectory();
    // dependency:resolve put every runtime artifact in the isolated repository
    assertThat(CliTestEnvironment.isolatedRepository()
        .resolve("org/eclipse/jetty/jetty-server")).isDirectory();
    String config = readFileContent(source("config", "Config"));
    assertThat(config)
        .contains("Booster.configure(\"local\"")
        .doesNotContain("\"production\"");
  }

  @Test
  @Order(2)
  @DisplayName("the generators add a command, an event, an entity and a read model")
  void generatesTheResources() {
    generate("new:event", "CartItemChanged", "--fields", "cartId:UUID", "productId:UUID", "quantity:number");
    JavaSources.edit(source("events", "CartItemChanged"), "/* the associated entity ID */", "cartId");

    generate("new:type", "CartItem", "--fields", "productId:UUID", "quantity:number");

    generate("new:entity", "Cart", "--fields", "items:Array<CartItem>", "--reduces", "CartItemChanged");
    assertThat(readFileContent(source("entities", "Cart")))
        .contains("import " + BASE_PACKAGE + ".common.CartItem;")
        .contains("@Reduces(CartItemChanged.class)");
    JavaSources.edit(
        source("entities", "Cart"),
        "/* NEW Cart HERE */",
        "new Cart(event.cartId(), java.util.stream.Stream.concat("
            + "(currentCart == null ? java.util.List.<CartItem>of() : currentCart.items()).stream()"
            + ".filter(item -> !item.productId().equals(event.productId())),"
            + " java.util.stream.Stream.of(new CartItem(event.productId(), event.quantity()))).toList())");

    generate("new:command", "ChangeCartItem", "--fields", "cartId:UUID", "productId:UUID", "quantity:number");
    JavaSources.edit(
        source("commands", "ChangeCartItem"),
        "/* Specify authorized roles here. Use All.class to authorize anyone */",
        "All.class",
        JavaSources.ALL);
    JavaSources.edit(
        source("commands", "ChangeCartItem"),
        "/* YOUR EVENT HERE */",
        "new CartItemChanged(command.cartId(), command.productId(), command.quantity())",
        BASE_PACKAGE + ".events.CartItemChanged");

    generate("new:read-model", "CartReadModel", "--fields", "items:Array<CartItem>", "--projects", "Cart:id");
    JavaSources.edit(
        source("readmodels", "CartReadModel"),
        "/* Specify authorized roles here. Use All.class to authorize anyone */",
        "All.class",
        JavaSources.ALL);
    JavaSources.edit(
        source("readmodels", "CartReadModel"),
        "/* NEW CartReadModel HERE */",
        "ProjectionResult.of(new CartReadModel(entity.id(), entity.items()))");
  }

  @Test
  @Order(3)
  @DisplayName("start builds and serves the application: a mutation and a query round-trip through GraphQL")
  void startsTheApplication() {
    httpPort = Ports.freePort();
    int webSocketPort = Ports.freePort();
    server = start(httpPort, webSocketPort);

    assertOutputSteps(
        new CliResult(project, List.of("start"), 0, server.stdout(), server.stderr()),
        "boost debug [local]",
        "Starting debug server on port " + httpPort);

    ApplicationTester applicationUnderTest = LocalApplication.tester(PROJECT_NAME, project, httpPort, webSocketPort);
    GraphQLClient client = applicationUnderTest.graphql().client();

    GraphQLResponse mutation =
        client.mutate(CHANGE_CART_ITEM, Map.of("cartId", cartId, "productId", firstProductId, "quantity", 2));
    assertThat(mutation.get("ChangeCartItem")).isEqualTo(true);

    GraphQLResponse cart = waitForCart(client, 1);
    assertThat(cart.get("CartReadModel.id")).isEqualTo(cartId);
    assertThat(cart.get("CartReadModel.items[0].productId")).isEqualTo(firstProductId);
    assertThat(((Number) cart.get("CartReadModel.items[0].quantity")).doubleValue()).isEqualTo(2.0);

    assertThat(applicationUnderTest.count().events()).isEqualTo(1);
    assertThat(applicationUnderTest.count().readModels("CartReadModel")).isEqualTo(1);
  }

  @Test
  @Order(4)
  @DisplayName("SIGTERM stops the CLI and the application")
  void sigtermStopsTheApplication() {
    List<ProcessHandle> descendants = server.descendants();
    assertThat(descendants).as("the application JVM started by the CLI").isNotEmpty();

    long stopping = System.nanoTime();
    server.terminate();
    int exitCode = server.awaitExit(Duration.ofSeconds(30));
    Duration stopTime = Duration.ofNanos(System.nanoTime() - stopping);

    // The JVM of the CLI ends with 128 + SIGTERM after its shutdown hook stopped the application
    assertThat(exitCode).as(server.result().describe()).isEqualTo(143);
    // The CLI kills the application when it has not stopped 10 s after the forwarded signal
    assertThat(stopTime).as("the application stopped on the forwarded SIGTERM").isLessThan(Duration.ofSeconds(10));
    assertThat(Processes.awaitTermination(descendants, Duration.ofSeconds(15))).isEmpty();
    assertThat(Ports.isListening(httpPort)).isFalse();
    assertThat(project.resolve(".booster/events.json")).isRegularFile();
    server = null;
  }

  @Test
  @Order(5)
  @DisplayName("a restarted application keeps the data and the entity state")
  void restartKeepsTheData() {
    httpPort = Ports.freePort();
    int webSocketPort = Ports.freePort();
    server = start(httpPort, webSocketPort);

    ApplicationTester applicationUnderTest = LocalApplication.tester(PROJECT_NAME, project, httpPort, webSocketPort);
    GraphQLClient client = applicationUnderTest.graphql().client();

    GraphQLResponse stored = client.query(CART_READ_MODEL, Map.of("cartId", cartId));
    assertThat(stored.get("CartReadModel.items[0].productId")).isEqualTo(firstProductId);

    // The reducer receives the cart rebuilt from the events stored before the restart
    client.mutate(CHANGE_CART_ITEM, Map.of("cartId", cartId, "productId", secondProductId, "quantity", 3));
    GraphQLResponse cart = waitForCart(client, 2);
    assertThat(cart.get("CartReadModel.items[0].productId")).isEqualTo(firstProductId);
    assertThat(cart.get("CartReadModel.items[1].productId")).isEqualTo(secondProductId);
    assertThat(((Number) cart.get("CartReadModel.items[1].quantity")).doubleValue()).isEqualTo(3.0);
    assertThat(applicationUnderTest.count().events()).isEqualTo(2);

    List<ProcessHandle> descendants = server.descendants();
    server.terminate();
    assertThat(server.awaitExit(Duration.ofSeconds(30))).isEqualTo(143);
    assertThat(Processes.awaitTermination(descendants, Duration.ofSeconds(15))).isEmpty();
    server = null;
  }

  RunningBoost start(int httpPort, int webSocketPort) {
    RunningBoost running =
        Boost.spawn(
            project,
            List.of("start", "--verbose", "-e", "local", "--port", Integer.toString(httpPort)),
            Map.of("LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT", Integer.toString(webSocketPort)));
    running.awaitListening(httpPort, START_TIMEOUT);
    return running;
  }

  /** Read models are eventually consistent: waits until the cart has the given number of items. */
  GraphQLResponse waitForCart(GraphQLClient client, int items) {
    return Sleep.waitForIt(
        () -> client.query(CART_READ_MODEL, Map.of("cartId", cartId)),
        response ->
            response.get("CartReadModel.items") instanceof List<?> list && list.size() == items
                ? (Object) Boolean.TRUE
                : "CartReadModel is " + response.get("CartReadModel"),
        250,
        60_000);
  }

  void generate(String... arguments) {
    CliResult result = Boost.run(project, arguments);
    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertThat(result.stderr()).as(result.describe()).isEmpty();
  }

  Path source(String packageName, String className) {
    return Sandbox.sourceFile(project, BASE_PACKAGE, packageName, className);
  }
}
