package io.boostercloud.booster.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.lang.model.SourceVersion;

/** Generates only into a new directory; an existing destination is never modified. */
public final class ProjectGenerator {
  private ProjectGenerator() {}

  public static Path create(Path destination, String packageName) throws IOException {
    if (packageName == null || !SourceVersion.isName(packageName)
        || !packageName.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) {
      throw new IllegalArgumentException("Package must be a valid lowercase Java package, for example com.example.cart");
    }
    Path root = destination.toAbsolutePath().normalize();
    if (root.getParent() == null || !Files.isDirectory(root.getParent())) {
      throw new IllegalArgumentException("The destination's parent directory must already exist");
    }
    Files.createDirectory(root); // Fails atomically if a directory, regular file or symlink already exists.
    Path main = Files.createDirectories(root.resolve("src/main/java/" + packageName.replace('.', '/')));
    Path tests = Files.createDirectories(root.resolve("src/test/java/" + packageName.replace('.', '/')));
    write(root.resolve("pom.xml"), pom(packageName));
    write(main.resolve("Application.java"), application(packageName));
    write(main.resolve("CartDomain.java"), domain(packageName));
    write(tests.resolve("CartDomainTest.java"), test(packageName));
    write(root.resolve(".gitignore"), "target/\n.booster-java/\n");
    write(root.resolve("README.md"), """
        # Booster Java application

        Requires Java 27, Maven, and `io.boostercloud:booster-java:0.1.0-SNAPSHOT`
        installed in your local Maven repository (`mvn install` in Booster's `java/` directory).

        ```sh
        mvn verify
        mvn exec:java
        ```

        The server binds to loopback on port 3000 and stores events under `.booster-java`.
        Stop with Ctrl+C. Restarting replays the event journal and restores projections.
        Configure `-Dbooster.port=3001` or `-Dbooster.data=/path/to/data` as needed.

        POST GraphQL operations to `/graphql`; GET `/health` for health status.
        The sample intentionally permits anonymous access. Replace `publicAccess()`
        with appropriate access policies and configure verified authentication for
        your application.

        `CartDomain.java` owns the commands, events, reducers, and read models. Its
        test exercises command execution, projection updates, and journal replay.
        """);
    return root;
  }

  private static void write(Path path, String text) throws IOException {
    Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private static String pom(String packageName) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>%s</groupId><artifactId>my-booster-app</artifactId><version>1.0-SNAPSHOT</version>
          <properties><maven.compiler.release>27</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
          <dependencies>
            <dependency><groupId>io.boostercloud</groupId><artifactId>booster-java</artifactId><version>0.1.0-SNAPSHOT</version></dependency>
            <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.13.4</version><scope>test</scope></dependency>
          </dependencies>
          <build><plugins>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.0</version></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.4</version></plugin>
            <plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.5.1</version><configuration><mainClass>%s.Application</mainClass></configuration></plugin>
          </plugins></build>
        </project>
        """.formatted(packageName, packageName);
  }

  private static String application(String packageName) {
    return """
        package %s;

        import io.boostercloud.booster.core.Booster;
        import io.boostercloud.booster.store.JdbcEventStore;
        import io.boostercloud.booster.transport.BoosterHttpServer;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.util.concurrent.CountDownLatch;

        public final class Application {
          public static void main(String[] args) throws Exception {
            Path data = Path.of(System.getProperty("booster.data", ".booster-java"));
            Files.createDirectories(data);
            int port = Integer.parseInt(System.getProperty("booster.port", "3000"));
            Booster booster = CartDomain.create(new JdbcEventStore(data.resolve("events")));
            BoosterHttpServer server = new BoosterHttpServer(booster, port);
            server.start();
            CountDownLatch stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
              server.close();
              booster.close();
              stopped.countDown();
            }));
            System.out.println("Booster application listening on port " + server.port());
            stopped.await();
          }
        }
        """.formatted(packageName);
  }

  private static String domain(String packageName) {
    return """
        package %s;

        import io.boostercloud.booster.core.AccessPolicy;
        import io.boostercloud.booster.core.Booster;
        import io.boostercloud.booster.core.EventStore;

        public final class CartDomain {
          public record AddItem(String cartId, int quantity, long unitPriceCents) {}
          public record ItemAdded(String cartId, int quantity, long unitPriceCents) {}
          public record Cart(String id, int quantity, long totalCents) {}
          public record CartView(String id, int quantity, long totalCents) {}
          public record Receipt(String id) {}

          public static Booster create(EventStore store) {
            return Booster.builder(store)
                .entity(Cart.class)
                .event(ItemAdded.class, ItemAdded::cartId, Cart.class, (event, previous) -> {
                  int quantity = Math.addExact(previous == null ? 0 : previous.quantity(), event.quantity());
                  long subtotal = Math.multiplyExact(event.quantity(), event.unitPriceCents());
                  long total = Math.addExact(previous == null ? 0 : previous.totalCents(), subtotal);
                  return new Cart(event.cartId(), quantity, total);
                })
                .command(AddItem.class, Receipt.class, AccessPolicy.publicAccess(), (command, register) -> {
                  if (command.cartId() == null || command.cartId().isBlank() || command.cartId().length() > 128)
                    throw new IllegalArgumentException("cartId must contain 1 to 128 characters");
                  if (command.quantity() < 1 || command.quantity() > 1000 || command.unitPriceCents() < 0
                      || command.unitPriceCents() > 1000000000L)
                    throw new IllegalArgumentException("Invalid quantity or unit price");
                  Cart previous = register.entity(Cart.class, command.cartId());
                  Math.addExact(previous == null ? 0 : previous.quantity(), command.quantity());
                  Math.addExact(previous == null ? 0 : previous.totalCents(),
                      Math.multiplyExact(command.quantity(), command.unitPriceCents()));
                  register.emit(new ItemAdded(command.cartId(), command.quantity(), command.unitPriceCents()));
                  return new Receipt(command.cartId());
                })
                .readModel(CartView.class, Cart.class, AccessPolicy.publicAccess(),
                    (cart, previous) -> new CartView(cart.id(), cart.quantity(), cart.totalCents()))
                .build();
          }
        }
        """.formatted(packageName);
  }

  private static String test(String packageName) {
    return """
        package %s;

        import io.boostercloud.booster.core.Json;
        import io.boostercloud.booster.core.RequestContext;
        import io.boostercloud.booster.store.JdbcEventStore;
        import org.junit.jupiter.api.Test;
        import static org.junit.jupiter.api.Assertions.assertEquals;

        class CartDomainTest {
          @Test void commandsUpdateTheProjectionAndReplayPreservesIt() {
            try (var app = CartDomain.create(JdbcEventStore.inMemory())) {
              app.executeCommand("AddItem", Json.MAPPER.valueToTree(new CartDomain.AddItem("cart-1", 2, 125)),
                  RequestContext.anonymous());
              var before = app.getReadModel("CartView", "cart-1", RequestContext.anonymous());
              assertEquals(250, before.path("totalCents").asLong());
              app.rebuild();
              assertEquals(before, app.getReadModel("CartView", "cart-1", RequestContext.anonymous()));
            }
          }
        }
        """.formatted(packageName);
  }
}
