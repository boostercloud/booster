package com.boostercloud.framework.types.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Wire format of the legacy {@code subscriptions-transport-ws} messages. */
class GraphQLWebsocketMessagesTest {

  @Test
  void parsesEveryClientMessage() {
    assertThat(BoosterJson.fromJson("{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer x\"}}",
        GraphQLClientMessage.class)).isEqualTo(new GraphQLInit(new GraphQLInit.Payload("Bearer x")));
    assertThat(BoosterJson.fromJson("{\"type\":\"connection_init\"}", GraphQLClientMessage.class))
        .isEqualTo(new GraphQLInit(new GraphQLInit.Payload(null)));
    GraphQLClientMessage start = BoosterJson.fromJson(
        "{\"type\":\"start\",\"id\":\"1\",\"payload\":{\"query\":\"subscription { x }\",\"variables\":{\"a\":1}}}",
        GraphQLClientMessage.class);
    assertThat(start).isEqualTo(new GraphQLStart("1", new GraphQLStart.Payload("subscription { x }", null, Map.of("a", 1))));
    assertThat(BoosterJson.fromJson("{\"type\":\"stop\",\"id\":\"1\"}", GraphQLClientMessage.class)).isEqualTo(new GraphQLStop("1"));
    assertThat(BoosterJson.fromJson("{\"type\":\"connection_terminate\"}", GraphQLClientMessage.class))
        .isEqualTo(new GraphQLTerminate());
  }

  @Test
  void rejectsUnknownMessageTypes() {
    assertThatThrownBy(() -> BoosterJson.fromJson("{\"type\":\"ka\"}", GraphQLClientMessage.class))
        .isInstanceOf(UncheckedIOException.class);
    assertThatThrownBy(() -> MessageTypes.fromValue("ka")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void writesClientMessagesWithTheirType() {
    assertThat(BoosterJson.toJson(new GraphQLStop("1"))).isEqualTo("{\"id\":\"1\",\"type\":\"stop\"}");
    assertThat(BoosterJson.toJson(new GraphQLInit(new GraphQLInit.Payload("Bearer x"))))
        .isEqualTo("{\"payload\":{\"Authorization\":\"Bearer x\"},\"type\":\"connection_init\"}");
  }

  @Test
  void writesServerMessagesLikeTheTypeScriptClasses() {
    assertThat(BoosterJson.toJson(new GraphQLInitAck())).isEqualTo("{\"type\":\"connection_ack\"}");
    assertThat(BoosterJson.toJson(new GraphQLInitError("Unauthorized")))
        .isEqualTo("{\"payload\":\"Unauthorized\",\"type\":\"connection_error\"}");
    assertThat(BoosterJson.toJson(new GraphQLData("1", Map.of("data", Map.of("x", 1)))))
        .isEqualTo("{\"id\":\"1\",\"payload\":{\"data\":{\"x\":1}},\"type\":\"data\"}");
    assertThat(BoosterJson.toJson(GraphQLErrorMessage.of("1", List.of(new IllegalArgumentException("bad")))))
        .isEqualTo("{\"id\":\"1\",\"payload\":{\"errors\":[{\"message\":\"bad\"}]},\"type\":\"error\"}");
    assertThat(BoosterJson.toJson(new GraphQLComplete("1"))).isEqualTo("{\"id\":\"1\",\"type\":\"complete\"}");
  }

  @Test
  void exposesTheProtocolConstants() {
    assertThat(GraphQLWebsocketMessages.GRAPHQL_WEBSOCKET_SUBPROTOCOL_HEADERS)
        .containsExactly(Map.entry("Sec-WebSocket-Protocol", "graphql-ws"));
    assertThat(MessageTypes.values()).extracting(MessageTypes::value).containsExactly(
        "connection_init", "connection_ack", "connection_error", "connection_terminate", "start", "data", "error",
        "complete", "stop");
  }
}
