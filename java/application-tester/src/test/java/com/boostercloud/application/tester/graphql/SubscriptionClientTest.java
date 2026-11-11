package com.boostercloud.application.tester.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.application.tester.fake.FakeGraphQLServer;
import com.boostercloud.application.tester.fake.FakeGraphQLServer.FakeSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SubscriptionClientTest {

  private static final Duration WAIT = Duration.ofSeconds(15);

  private FakeGraphQLServer server;
  private final List<SubscriptionClient> clients = new ArrayList<>();

  @BeforeEach
  void start() {
    server = FakeGraphQLServer.start();
  }

  @AfterEach
  void stop() {
    clients.forEach(SubscriptionClient::close);
    server.close();
  }

  private SubscriptionClient client(SubscriptionClientOptions options) {
    SubscriptionClient client = new SubscriptionClient(server.websocketUrl(), options);
    clients.add(client);
    return client;
  }

  private FakeSocket socket(int index) {
    await().atMost(WAIT).until(() -> server.sockets().size() > index);
    return server.sockets().get(index);
  }

  private static Operation operation(String document) {
    return Operation.prepare(document, null, false);
  }

  /** Records the raw callbacks of an operation. */
  private static final class Recorder implements OperationObserver {
    final List<Object> events = new CopyOnWriteArrayList<>();

    @Override
    public void next(Map<String, Object> result) {
      events.add(result);
    }

    @Override
    public void error(Throwable error) {
      events.add(error);
    }

    @Override
    public void complete() {
      events.add("complete");
    }
  }

  @Nested
  @DisplayName("events")
  class Events {

    @Test
    @DisplayName("emits connecting and connected, then disconnected on close")
    void connectEvents() {
      List<String> events = new CopyOnWriteArrayList<>();
      AtomicInteger callbacks = new AtomicInteger();
      SubscriptionClient client = client(SubscriptionClientOptions.builder()
          .lazy(true)
          .connectionCallback(error -> callbacks.incrementAndGet())
          .build());
      client.onConnecting(() -> events.add("connecting"));
      client.onConnected(payload -> events.add("connected"));
      client.onDisconnected(() -> events.add("disconnected"));
      Runnable removed = client.onReconnected(payload -> events.add("reconnected"));
      removed.run();

      client.request(operation("subscription S { a }")).subscribe(new Recorder());
      await().atMost(WAIT).until(() -> events.size() == 2);
      client.close();

      assertThat(events).containsExactly("connecting", "connected", "disconnected");
      assertThat(callbacks).hasValue(1);
    }

    @Test
    @DisplayName("reports messages that cannot be sent while closed and without reconnection")
    void notSent() {
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      SubscriptionClient client = client(SubscriptionClientOptions.defaults());
      client.onError(errors::add);
      SubscriptionClient.Unsubscribable handle = client.request(operation("subscription S { a }")).subscribe(new Recorder());
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 2);

      socket.close(1000, "bye");
      await().atMost(WAIT).until(() -> client.status() == SubscriptionClient.Status.CLOSED);
      handle.unsubscribe();

      await().atMost(WAIT).until(() -> !errors.isEmpty());
      assertThat(errors.getFirst()).hasMessage(
          "A message was not sent because socket is not connected, is closing or is already closed. Message was: "
              + "{\"id\":\"1\",\"type\":\"stop\"}");
    }

    @Test
    @DisplayName("reports an unusable URL as a socket error and stays closed without reconnection")
    void invalidUrl() {
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      SubscriptionClient client =
          new SubscriptionClient("http://127.0.0.1:1/websocket", SubscriptionClientOptions.builder().lazy(true).build());
      clients.add(client);
      client.onError(errors::add);

      client.request(operation("subscription S { a }")).subscribe(new Recorder());

      await().atMost(WAIT).until(() -> !errors.isEmpty());
      await().atMost(WAIT).until(() -> client.status() == SubscriptionClient.Status.CLOSED);
    }

    @Test
    @DisplayName("reports unparseable and unknown messages to the error listeners")
    void badMessages() {
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      SubscriptionClient client = client(SubscriptionClientOptions.defaults());
      client.onError(errors::add);
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 1);

      socket.sendText("not json");
      socket.sendText("{\"type\":\"surprise\"}");

      await().atMost(WAIT).until(() -> errors.size() == 2);
      assertThat(errors).extracting(Throwable::getMessage)
          .containsExactly("Message must be JSON-parseable. Got: not json", "Invalid message type!");
    }
  }

  @Nested
  @DisplayName("options")
  class Options {

    @Test
    @DisplayName("lazy clients connect on the first operation, queueing it until the socket opens")
    void lazy() {
      SubscriptionClient client = client(SubscriptionClientOptions.builder().lazy(true).build());
      assertThat(client.status()).isEqualTo(SubscriptionClient.Status.CLOSED);
      Sleep200.sleep();
      assertThat(server.sockets()).isEmpty();

      client.request(operation("subscription S { a }")).subscribe(new Recorder());

      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 2);
      assertThat(socket.types()).containsExactly("connection_init", "start");
    }

    @Test
    @DisplayName("connection params are evaluated on each connection; a failure sends connection_error with {}")
    void connectionParams() {
      AtomicInteger calls = new AtomicInteger();
      SubscriptionClient client = client(SubscriptionClientOptions.builder()
          .reconnect(true)
          .connectionParams(() -> {
            if (calls.incrementAndGet() == 2) {
              throw new IllegalStateException("no token");
            }
            return Map.of("Authorization", "Bearer " + calls.get());
          })
          .build());
      FakeSocket first = socket(0);
      await().atMost(WAIT).until(() -> first.receivedTexts().size() == 1);
      assertThat(first.receivedTexts())
          .containsExactly("{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer 1\"}}");

      client.close(false);

      FakeSocket second = socket(1);
      await().atMost(WAIT).until(() -> second.receivedTexts().size() == 1);
      assertThat(second.receivedTexts()).containsExactly("{\"type\":\"connection_error\",\"payload\":{}}");
    }

    @Test
    @DisplayName("an inactivity timeout closes the socket after the last operation stops")
    void inactivityTimeout() {
      SubscriptionClient client = client(SubscriptionClientOptions.builder().inactivityTimeout(300).build());
      SubscriptionClient.Unsubscribable handle = client.request(operation("subscription S { a }")).subscribe(new Recorder());
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 2);

      handle.unsubscribe();

      await().atMost(WAIT).until(() -> !socket.isOpen());
      assertThat(socket.types()).containsExactly("connection_init", "start", "stop", "connection_terminate");
    }

    @Test
    @DisplayName("reconnection stops after reconnectionAttempts")
    void reconnectionAttempts() {
      SubscriptionClient client = client(SubscriptionClientOptions.builder()
          .reconnect(true).reconnectionAttempts(2).random(() -> 0.0).build());
      socket(0);
      await().atMost(WAIT).until(() -> client.status() == SubscriptionClient.Status.OPEN);
      server.stopServer();
      // attempts after 100 ms and 200 ms, then it gives up
      Sleep200.sleep();
      Sleep200.sleep();
      Sleep200.sleep();
      Sleep200.sleep();
      server.startServer();
      Sleep200.sleep();
      Sleep200.sleep();
      Sleep200.sleep();
      assertThat(server.sockets()).hasSize(1);
      assertThat(client.status()).isEqualTo(SubscriptionClient.Status.CLOSED);
    }

    @Test
    @DisplayName("keep-alive messages arm a check that reconnects when they stop arriving")
    void keepAlive() {
      SubscriptionClient client = client(SubscriptionClientOptions.builder().reconnect(true).timeout(300).build());
      FakeSocket first = socket(0);
      await().atMost(WAIT).until(() -> client.status() == SubscriptionClient.Status.OPEN);
      for (int i = 0; i < 4; i++) {
        first.send(Map.of("type", "ka"));
        Sleep200.sleep();
      }
      assertThat(server.sockets()).hasSize(1);

      // no more ka: the next checks find none and reconnect
      FakeSocket second = socket(1);
      assertThat(second.requestedProtocols()).containsExactly("graphql-ws");
    }
  }

  @Nested
  @DisplayName("operations")
  class Operations {

    @Test
    @DisplayName("each subscribe of a request starts the operation with a new id; unsubscribe is idempotent")
    void requestSubscribe() {
      SubscriptionClient client = client(SubscriptionClientOptions.defaults());
      SubscriptionClient.OperationRequest request = client.request(operation("subscription S { a }"));
      SubscriptionClient.Unsubscribable first = request.subscribe(new Recorder());
      request.subscribe(new Recorder());
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 3);
      assertThat(client.activeOperationIds()).containsExactly("1", "2");

      first.unsubscribe();
      first.unsubscribe();
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 4);
      Sleep200.sleep();
      assertThat(socket.messages("stop")).hasSize(1);
      client.unsubscribeAll();
      await().atMost(WAIT).until(() -> socket.messages("stop").size() == 2);
      assertThat(client.activeOperationIds()).isEmpty();
    }

    @Test
    @DisplayName("data payload errors are formatted and passed to the raw observer")
    void dataErrors() {
      SubscriptionClient client = client(SubscriptionClientOptions.defaults());
      Recorder recorder = new Recorder();
      client.request(operation("subscription S { a }")).subscribe(recorder);
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> socket.receivedTexts().size() == 2);

      socket.send(Map.of("id", "1", "type", "data", "payload", Map.of("errors", Map.of("message", "bad"))));

      await().atMost(WAIT).until(() -> recorder.events.size() == 1);
      assertThat(recorder.events.getFirst()).isEqualTo(Map.of("errors", List.of(Map.of("message", "bad"))));
    }

    @Test
    @DisplayName("an empty query fails the operation")
    void emptyQuery() {
      SubscriptionClient client = client(SubscriptionClientOptions.defaults());
      Recorder recorder = new Recorder();
      client.request(new Operation("", null, null, null, true)).subscribe(recorder);
      assertThat(recorder.events).hasSize(1);
      assertThat((Throwable) recorder.events.getFirst()).hasMessage("Must provide a query.");
      assertThat(client.activeOperationIds()).isEmpty();
    }
  }

  @Nested
  @DisplayName("helpers")
  class Helpers {

    @Test
    @DisplayName("formatErrors always returns a list of errors")
    void formatErrors() {
      assertThat(SubscriptionClient.formatErrors(List.of(Map.of("message", "a"), "b")))
          .extracting(GraphQLError::toMap)
          .containsExactly(Map.of("message", "a"), Map.of("message", "b"));
      assertThat(SubscriptionClient.formatErrors(Map.of("errors", List.of(Map.of("message", "nested")))))
          .extracting(GraphQLError::message).containsExactly("nested");
      assertThat(SubscriptionClient.formatErrors(Map.of("message", "single", "code", 1)))
          .extracting(GraphQLError::toMap).containsExactly(Map.of("message", "single", "code", 1));
      assertThat(SubscriptionClient.formatErrors("text")).extracting(GraphQLError::toMap).containsExactly(
          Map.of("name", "FormatedError", "message", "Unknown error", "originalError", "text"));
    }

    @Test
    @DisplayName("Backoff doubles from 100 ms with jitter, capped at the maximum, and resets")
    void backoff() {
      double[] randoms = {0.0, 0.375, 0.875};
      AtomicInteger index = new AtomicInteger();
      Backoff jittered = new Backoff(100, 10000, 2, 0.5, () -> randoms[index.getAndIncrement() % randoms.length]);
      // rand 0.0: floor(0) even -> minus 0; rand 0.375: floor(3.75)=3 odd -> plus floor(0.375*0.5*200)=37;
      // rand 0.875: floor(8.75)=8 even -> minus floor(0.875*0.5*400)=175
      assertThat(jittered.duration()).isEqualTo(100);
      assertThat(jittered.duration()).isEqualTo(237);
      assertThat(jittered.duration()).isEqualTo(225);
      assertThat(jittered.attempts()).isEqualTo(3);
      jittered.reset();
      assertThat(jittered.attempts()).isZero();

      Backoff connect = new Backoff(1000, 30000, 1.2, 0, Math::random);
      assertThat(connect.duration()).isEqualTo(1000);
      assertThat(connect.duration()).isEqualTo(1200);
      assertThat(connect.duration()).isEqualTo(1440);
      for (int i = 0; i < 30; i++) {
        connect.duration();
      }
      assertThat(connect.duration()).isEqualTo(30000);
    }

    @Test
    @DisplayName("ApolloError messages join GraphQL and network messages")
    void apolloErrorMessage() {
      GraphQLClientException both = new GraphQLClientException(
          List.of(GraphQLError.ofMessage("a"), GraphQLError.ofMessage("b")), new IllegalStateException("net"), null);
      assertThat(both).hasMessage("a\nb\nnet");
      assertThat(both.getCause()).hasMessage("net");
      assertThat(new GraphQLClientException(List.of(), null, "explicit")).hasMessage("explicit");
      assertThat(GraphQLClientException.ofNetworkError(new IllegalStateException("only"))).hasMessage("only");
    }

    @Test
    @DisplayName("AuthToken follows TypeScript truthiness")
    void authToken() {
      assertThat(AuthToken.of("t").authorization()).contains("Bearer t");
      assertThat(AuthToken.of("").authorization()).isEmpty();
      assertThat(AuthToken.none().authorization()).isEmpty();
      AtomicInteger calls = new AtomicInteger();
      AuthToken dynamic = AuthToken.of(() -> "t" + calls.incrementAndGet());
      assertThat(dynamic.authorization()).contains("Bearer t1");
      assertThat(dynamic.authorization()).contains("Bearer t2");
    }
  }

  /** Short pause helper. */
  private static final class Sleep200 {
    static void sleep() {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
