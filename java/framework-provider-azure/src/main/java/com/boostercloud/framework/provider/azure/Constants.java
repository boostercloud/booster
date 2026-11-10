package com.boostercloud.framework.provider.azure;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code constants.ts}: attribute names of the Cosmos DB containers, the names of the app settings
 * (environment variables) the runtime reads, and the Cosmos DB status codes the adapters react to.
 *
 * <p>The infrastructure module ({@code framework-provider-azure-infrastructure}) uses the same constants to create the
 * containers and the function app settings, so the values must never change independently.
 */
public final class Constants {

  private Constants() {}

  /** {@code eventsStoreAttributes}: the events container ({@code <app>-app-events-store}). */
  public static final class EventsStoreAttributes {
    /** Partition key path of the events container (value {@code <entityTypeName>-<entityID>-<kind>}). */
    public static final String PARTITION_KEY = "entityTypeName_entityID_kind";
    /** Sort key: the ISO creation timestamp. */
    public static final String SORT_KEY = "createdAt";

    private EventsStoreAttributes() {}
  }

  /** {@code subscriptionsStoreAttributes}: the GraphQL subscriptions container. */
  public static final class SubscriptionsStoreAttributes {
    /** Partition key path: the read model name. */
    public static final String PARTITION_KEY = "className";
    /** Sort key: {@code <connectionID>-<subscriptionID>}. */
    public static final String SORT_KEY = "connectionID_subscriptionID";
    /** Envelope attribute copied into the per-item {@code ttl}. */
    public static final String TTL = "expirationTime";
    /** Attribute used to find the subscriptions of a connection. */
    public static final String INDEX_BY_CONNECTION_ID_PARTITION_KEY = "connectionID";
    /** Attribute used to find one subscription of a connection. */
    public static final String INDEX_BY_CONNECTION_ID_SORT_KEY = "subscriptionID";

    private SubscriptionsStoreAttributes() {}

    /**
     * {@code indexByConnectionIDName(config)} (defined for parity; unused by the runtime, as in TypeScript).
     *
     * @param config the configuration
     * @return {@code <subscriptionsStore>-index-by-connection}
     */
    public static String indexByConnectionIDName(BoosterConfig config) {
      return config.resourceNames().subscriptionsStore() + "-index-by-connection";
    }
  }

  /** {@code connectionsStoreAttributes}: the websocket connections container. */
  public static final class ConnectionsStoreAttributes {
    /** Partition key path: the connection id. */
    public static final String PARTITION_KEY = "connectionID";
    /** Envelope attribute copied into the per-item {@code ttl}. */
    public static final String TTL = "expirationTime";

    private ConnectionsStoreAttributes() {}
  }

  /** {@code dedupAttributes}: the event stream de-duplication container. */
  public static final class DedupAttributes {
    /** Partition key (and unique key) path. */
    public static final String PARTITION_KEY = "primaryKey";
    /** Defined for parity; the dedup records use the literal {@code ttl} property, as in TypeScript. */
    public static final String TTL = "expirationTime";

    private DedupAttributes() {}
  }

  /** {@code environmentVarNames}: the app settings read by the Azure runtime. */
  public static final class EnvironmentVarNames {
    /** Base URL of the public REST API (the GraphQL endpoint is {@code <url>/graphql}). */
    public static final String REST_API_URL = "BOOSTER_REST_API_URL";
    /** Defined for parity; not read by the runtime. */
    public static final String WEBSOCKET_API_URL = "BOOSTER_WEBSOCKET_API_URL";
    /** {@code AccountEndpoint=https://...;AccountKey=...;} */
    public static final String COSMOS_DB_CONNECTION_STRING = "COSMOSDB_CONNECTION_STRING";
    /** Maximum number of retries of throttled (429) Cosmos DB requests. */
    public static final String COSMOS_DB_MAX_RETRIES = "COSMOSDB_MAX_RETRIES";
    /** Fixed retry interval (ms) of throttled Cosmos DB requests (instead of the server's retry-after). */
    public static final String COSMOS_DB_RETRY_INTERVAL = "COSMOSDB_RETRY_INTERVAL";
    /** Maximum cumulative wait (s) for throttled Cosmos DB requests. */
    public static final String COSMOS_DB_MAX_WAIT_TIME = "COSMOSDB_MAX_WAIT_TIME";
    /** {@code Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...;EntityPath=...} */
    public static final String EVENT_HUB_CONNECTION_STRING = "EVENTHUB_CONNECTION_STRING";
    /** Name of the event hub (the stream topic). */
    public static final String EVENT_HUB_NAME = "EVENTHUB_NAME";
    /** Maximum retries of the Event Hubs producer (default 5). */
    public static final String EVENT_HUB_MAX_RETRIES = "EVENTHUB_MAX_RETRIES";
    /** {@code exponential} (default) or {@code fixed}; only honoured when {@link #EVENT_HUB_MAX_RETRIES} is set. */
    public static final String EVENT_HUB_MODE = "EVENTHUB_MODE";
    /** Comma-separated function app names of the rockets (written by the infrastructure only). */
    public static final String ROCKET_FUNCTION_APP_NAMES = "ROCKET_FUNCTION_APP_NAMES";
    /** {@code pkgA:funcAppA;pkgB:funcAppB}, read by the rocket health checks. */
    public static final String ROCKET_PACKAGE_MAPPING = "ROCKET_PACKAGE_MAPPING";

    private EnvironmentVarNames() {}
  }

  /**
   * App settings read by the runtime that are not part of {@code environmentVarNames} in TypeScript (they are read
   * as literal {@code process.env} keys there), plus the Java-only ones.
   */
  public static final class OtherEnvironmentVarNames {
    /** Web PubSub connection string (mixed case, as in TypeScript). */
    public static final String WEB_PUB_SUB_CONNECTION_STRING = "WebPubSubConnectionString";
    /** Azure Functions instance id, only used in a debug log line. */
    public static final String WEBSITE_INSTANCE_ID = "WEBSITE_INSTANCE_ID";
    /**
     * Java-only: fully qualified name of the application class passed to {@code Booster.start} by the Azure
     * Functions entry points (TypeScript requires {@code dist/index.js} instead).
     */
    public static final String BOOSTER_APP_CLASS = "BOOSTER_APP_CLASS";

    private OtherEnvironmentVarNames() {}
  }

  /** {@code AZURE_CONFLICT_ERROR_CODE}: HTTP 409 returned by Cosmos DB on id or unique key conflicts. */
  public static final int AZURE_CONFLICT_ERROR_CODE = 409;

  /** {@code AZURE_PRECONDITION_FAILED_ERROR}: HTTP 412 returned by Cosmos DB when an If-Match condition fails. */
  public static final int AZURE_PRECONDITION_FAILED_ERROR = 412;

  /** The Web PubSub hub used by Booster ({@code 'booster'} literal in TypeScript). */
  public static final String WEB_PUB_SUB_HUB = "booster";
}
