package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One binding of a {@code function.json} ({@code Binding} of {@code types/functionDefinition.ts} and its refinements
 * {@code HttpBinding}, {@code ScheduleBinding}, {@code GraphQLBinding}, {@code EventHandlerBinding},
 * {@code EventHubInputBinding}, {@code EventHubOutBinding}, {@code SubscriptionBinding}, {@code SocketsBinding}).
 *
 * <p>TypeScript describes each kind with a structural type and an index signature; Java keeps one immutable, ordered
 * property map (the JSON object, in the order the properties were given, which is the order {@code JSON.stringify}
 * writes) with {@code type}, {@code name} and {@code direction} required, and factory methods for the bindings the
 * Booster functions use.
 */
public final class Binding {

  /** {@code direction: 'in'}. */
  public static final String IN = "in";
  /** {@code direction: 'out'}. */
  public static final String OUT = "out";
  /** The name of the binding that receives the function's return value. */
  public static final String RETURN = "$return";

  private final Map<String, Object> properties;

  private Binding(Map<String, Object> properties) {
    this.properties = Collections.unmodifiableMap(properties);
  }

  /**
   * @param properties the binding properties, in JSON order (must contain string {@code type}, {@code name} and
   *     {@code direction}; {@code null} values are dropped)
   * @return the binding
   * @throws IllegalArgumentException when a required property is missing
   */
  public static Binding of(Map<String, ?> properties) {
    Map<String, Object> copy = new LinkedHashMap<>();
    properties.forEach((key, value) -> {
      if (value != null) {
        copy.put(key, value instanceof List<?> list ? List.copyOf(list) : value);
      }
    });
    for (String required : List.of("type", "name", "direction")) {
      if (!(copy.get(required) instanceof String)) {
        throw new IllegalArgumentException("A binding needs a string '" + required + "' property: " + copy);
      }
    }
    return new Binding(copy);
  }

  /**
   * @param type the binding type
   * @param name the binding name
   * @param direction {@code in} or {@code out}
   * @return a binding with the three properties in that order
   */
  public static Binding of(String type, String name, String direction) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", type);
    properties.put("name", name);
    properties.put("direction", direction);
    return of(properties);
  }

  /**
   * @param key a property name
   * @param value the value ({@code null} removes the property)
   * @return a copy with the property added at the end (or replaced in place)
   */
  public Binding with(String key, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(properties);
    if (value == null) {
      copy.remove(key);
    } else {
      copy.put(key, value);
    }
    return of(copy);
  }

  /**
   * @return {@code type}
   */
  public String type() {
    return (String) properties.get("type");
  }

  /**
   * @return {@code name}
   */
  public String name() {
    return (String) properties.get("name");
  }

  /**
   * @return {@code direction}
   */
  public String direction() {
    return (String) properties.get("direction");
  }

  /**
   * @param key a property name
   * @return its value, or {@code null}
   */
  public Object get(String key) {
    return properties.get(key);
  }

  /**
   * @return the properties in JSON order (unmodifiable)
   */
  public Map<String, Object> properties() {
    return properties;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // The bindings of the Java worker functions (the Java counterpart of the triggers registered by functions.js)
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * An HTTP trigger ({@code app.http(...)}).
   *
   * @param name the binding name ({@code request} for the Booster entry points)
   * @param methods the HTTP methods, e.g. {@code POST}
   * @param authLevel the authorization level, e.g. {@code anonymous}
   * @param route the route, or {@code null} for the default ({@code api/<function name>})
   * @return the binding
   */
  public static Binding httpTrigger(String name, List<String> methods, String authLevel, String route) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "httpTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("methods", methods);
    properties.put("authLevel", authLevel);
    properties.put("route", route);
    return of(properties);
  }

  /**
   * @return the HTTP output binding that returns the function's result ({@code {type: 'http', name: '$return'}})
   */
  public static Binding httpReturn() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "http");
    properties.put("direction", OUT);
    properties.put("name", RETURN);
    return of(properties);
  }

  /**
   * A Cosmos DB change feed trigger ({@code app.cosmosDB(...)}) delivering the documents as a JSON string.
   *
   * @param name the binding name ({@code documents} for the Booster entry points)
   * @param connection the app setting with the connection string
   * @param databaseName the database
   * @param containerName the monitored container
   * @param leaseContainerName the lease container
   * @return the binding
   */
  public static Binding cosmosDBTrigger(
      String name, String connection, String databaseName, String containerName, String leaseContainerName) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "cosmosDBTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("connection", connection);
    properties.put("databaseName", databaseName);
    properties.put("containerName", containerName);
    properties.put("createLeaseContainerIfNotExists", true);
    properties.put("leaseContainerName", leaseContainerName);
    properties.put("dataType", "string");
    return of(properties);
  }

  /**
   * A generic Web PubSub trigger ({@code trigger.generic({ type: 'webPubSubTrigger', ... })}).
   *
   * @param name the binding name ({@code request})
   * @param hub the hub ({@code booster})
   * @param eventType {@code system} or {@code user}
   * @param eventName {@code connect}, {@code disconnect} or {@code message}
   * @param connection the app setting with the Web PubSub connection string
   * @return the binding
   */
  public static Binding webPubSubTrigger(
      String name, String hub, String eventType, String eventName, String connection) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "webPubSubTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("hub", hub);
    properties.put("eventType", eventType);
    properties.put("eventName", eventName);
    properties.put("connection", connection);
    return of(properties);
  }

  /**
   * A timer trigger ({@code app.timer(...)}).
   *
   * @param name the binding name ({@code timer})
   * @param schedule the NCRONTAB expression ({@code {second} {minute} {hour} {day} {month} {day-of-week}})
   * @return the binding
   */
  public static Binding timerTrigger(String name, String schedule) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "timerTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("schedule", schedule);
    return of(properties);
  }

  /**
   * An Event Hub trigger ({@code app.eventHub(...)}) delivering the bodies of a batch as strings.
   *
   * @param name the binding name ({@code messages})
   * @param connection the app setting with the connection string
   * @param eventHubName the event hub
   * @param cardinality {@code many} or {@code one}
   * @return the binding
   */
  public static Binding eventHubTrigger(String name, String connection, String eventHubName, String cardinality) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "eventHubTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("connection", connection);
    properties.put("eventHubName", eventHubName);
    properties.put("cardinality", cardinality);
    properties.put("dataType", "string");
    return of(properties);
  }

  /**
   * A blob trigger ({@code app.storageBlob(...)}, used by the file-uploads rocket) delivering the content as bytes.
   *
   * @param name the binding name ({@code blob})
   * @param path the blob path pattern, e.g. {@code <container>/{name}}
   * @param connection the app setting (prefix) of the storage connection
   * @return the binding
   */
  public static Binding blobTrigger(String name, String path, String connection) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", "blobTrigger");
    properties.put("direction", IN);
    properties.put("name", name);
    properties.put("path", path);
    properties.put("connection", connection);
    properties.put("dataType", "binary");
    return of(properties);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Binding binding && properties.equals(binding.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(properties);
  }

  @Override
  public String toString() {
    return "Binding" + properties;
  }
}
