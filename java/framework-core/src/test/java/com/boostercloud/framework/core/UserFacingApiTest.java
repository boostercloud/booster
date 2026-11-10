package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Compiles an application written with the user-facing API of ARCHITECTURE.md §6b (the shapes the CLI generators
 * emit) against the real framework, boots it and runs a command and a query through the dispatchers.
 */
class UserFacingApiTest extends BoosterTestSupport {

  private static final List<String> SOURCES = List.of("Main", "Config", "ChangeCartItem", "CartItemChanged",
      "CartAbandoned", "Cart", "CartReadModel", "CartTotalQuantity", "LogCartItemChanged", "CheckCarts", "Admin",
      "CartItem", "Address", "ValidateInput", "CheckAccess");

  @Test
  @SuppressWarnings({"deprecation", "unchecked"})
  void bootsAnApplicationWrittenWithTheDocumentedApi(@TempDir Path temp) throws Exception {
    Path sources = temp.resolve("src");
    Files.createDirectories(sources);
    List<String> arguments = new ArrayList<>(List.of("-d", temp.resolve("classes").toString(), "-parameters",
        "-classpath", System.getProperty("java.class.path"), "-proc:none", "-Xlint:none"));
    for (String name : SOURCES) {
      Path file = sources.resolve(name + ".java");
      try (InputStream source = getClass().getResourceAsStream("/userapp/" + name + ".java")) {
        Files.write(file, source.readAllBytes());
      }
      arguments.add(file.toString());
    }
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new))).isZero();

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temp.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
      Booster.start(loader.loadClass("com.example.store.Main"));

      BoosterConfig config = Booster.config();
      assertThat(config.appName).isEqualTo("my-store");
      assertThat(Booster.configuredEnvironments()).containsExactly("test", "production");
      assertThat(config.commandHandlers).containsOnlyKeys("ChangeCartItem");
      assertThat(config.commandHandlers.get("ChangeCartItem").authorizer()).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
      assertThat(config.nonExposedGraphQLMetadataKey).containsEntry("ChangeCartItem", List.of("test"));
      assertThat(config.events).containsKeys("CartItemChanged");
      assertThat(config.notifications).containsOnlyKeys("CartAbandoned");
      assertThat(config.partitionKeys).containsEntry("CartAbandoned", "cartId");
      assertThat(config.topicToEvent).containsEntry("cart-abandoned", "CartAbandoned");
      assertThat(config.entities.get("Cart").eventStreamAuthorizer()).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
      assertThat(config.reducers.get("CartItemChanged").methodName()).isEqualTo("changeItem");
      assertThat(config.readModels.get("CartReadModel").before()).hasSize(1);
      assertThat(config.projections.get("Cart")).singleElement()
          .satisfies(projection -> assertThat(projection.joinKey()).isEqualTo(JoinKey.property("id")));
      assertThat(config.unProjections.get("Cart")).singleElement()
          .satisfies(projection -> assertThat(projection.methodName()).isEqualTo("removeCart"));
      assertThat(config.queryHandlers).containsOnlyKeys("CartTotalQuantity");
      assertThat(config.eventHandlers.get("CartItemChanged")).extracting(Class::getSimpleName).containsExactly("LogCartItemChanged");
      assertThat(config.eventHandlers.get(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS))
          .extracting(Class::getSimpleName).containsExactly("LogAll");
      assertThat(config.scheduledCommandHandlers.get("CheckCarts").scheduledOn().minute()).isEqualTo("0/5");
      assertThat(config.roles.get("Admin").auth().signUpMethods()).hasSize(1);

      ProviderLibrary provider = mock(ProviderLibrary.class);
      ProviderEventsLibrary events = mock(ProviderEventsLibrary.class);
      when(provider.events()).thenReturn(events);
      when(events.store(anyList(), any())).thenReturn(List.of());
      config.setProvider(provider);
      GraphQLResolverContext context = new GraphQLResolverContext(null, new LinkedHashMap<>(), null, UUID.of("r"), null, true, null, null);

      Map<String, Object> input = Map.of("cartId", "cart-1", "productId", "product-1", "quantity", 2);
      Object result = new BoosterCommandDispatcher(config).dispatchCommand(
          CommandEnvelope.builder().requestID(UUID.of("r")).typeName("ChangeCartItem").version(1).value(input).build(), context);
      assertThat(result).isNull();
      ArgumentCaptor<List<NonPersistedEventEnvelope>> stored = ArgumentCaptor.forClass(List.class);
      verify(events).store(stored.capture(), any());
      assertThat(stored.getValue()).singleElement().satisfies(event -> {
        assertThat(event.typeName()).isEqualTo("CartItemChanged");
        assertThat(event.entityTypeName()).isEqualTo("Cart");
        assertThat(event.entityID()).isEqualTo(UUID.of("cart-1"));
      });
      assertThatThrownBy(() -> new BoosterCommandDispatcher(config).dispatchCommand(CommandEnvelope.builder()
          .requestID(UUID.of("r")).typeName("ChangeCartItem").version(1)
          .value(Map.of("cartId", "c", "productId", "p", "quantity", -1)).build(), context))
          .hasMessage("Negative quantity");

      Object total = new BoosterQueryDispatcher(config).dispatchQuery(QueryEnvelope.builder().requestID(UUID.of("r"))
          .typeName("CartTotalQuantity").version(1).value(Map.of("cartId", "cart-1")).build(), context);
      assertThat(total).isEqualTo(3.0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
