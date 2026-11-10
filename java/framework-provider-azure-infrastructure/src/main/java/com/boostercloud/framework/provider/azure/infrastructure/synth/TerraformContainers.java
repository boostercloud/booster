package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.Constants.ConnectionsStoreAttributes;
import com.boostercloud.framework.provider.azure.Constants.DedupAttributes;
import com.boostercloud.framework.provider.azure.Constants.EventsStoreAttributes;
import com.boostercloud.framework.provider.azure.Constants.SubscriptionsStoreAttributes;
import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ResourceNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code synth/terraform-containers.ts}: the {@code azurerm_cosmosdb_sql_container}s. Every container uses
 * partition key version 2 and autoscale up to {@code AZURE_MAX_CONTAINER_THROUGHPUT}.
 *
 * <table>
 *   <caption>Containers</caption>
 *   <tr><th>Container</th><th>Construct id</th><th>Partition key</th><th>Extra</th></tr>
 *   <tr><td>events store</td><td>{@code id('events')}</td><td>{@code /entityTypeName_entityID_kind}</td><td></td></tr>
 *   <tr><td>dispatched events</td><td>{@code id('dispatched-events')}</td><td>{@code /eventId}</td>
 *       <td>unique key {@code /eventId}, TTL {@code config.dispatchedEventsTtl}</td></tr>
 *   <tr><td>each read model</td><td>{@code toTerraformName(name)}</td><td>{@code /id}</td><td></td></tr>
 *   <tr><td>subscriptions (subscriptions on)</td><td>{@code id('subscriptions-table')}</td><td>{@code /className}</td>
 *       <td>TTL -1</td></tr>
 *   <tr><td>connections (subscriptions on)</td><td>{@code id('connections-table')}</td><td>{@code /connectionID}</td>
 *       <td>TTL -1</td></tr>
 *   <tr><td>events dedup (event streaming on)</td><td>{@code id('dedup-table')}</td><td>{@code /primaryKey}</td>
 *       <td>unique key {@code /primaryKey}, TTL -1</td></tr>
 * </table>
 */
public final class TerraformContainers {

  private TerraformContainers() {}

  /**
   * Creates the events, dispatched events, read model and (with subscriptions) subscriptions and connections
   * containers.
   *
   * <p>Quirk kept from TypeScript: without subscriptions the dispatched events container is created but not part of
   * the returned list.
   *
   * @param stack the stack bag
   * @param config the configuration
   * @return {@code [events, dispatched, subscriptions, connections, ...readModels]} with subscriptions, else
   *     {@code [events, ...readModels]}
   * @throws IllegalStateException {@code Undefined cosmosdbDatabase resource} / {@code Undefined cosmosdbSqlDatabase
   *     resource}
   */
  public static List<TerraformResource> build(ApplicationSynthStack stack, BoosterConfig config) {
    checkDatabases(stack);
    ResourceNames names = config.resourceNames();
    TerraformResource eventContainer = container(stack, Utils.toTerraformName(stack.appPrefix, "events"),
        Attributes.of(
            "name", names.eventsStore(),
            "partition_key_paths", List.of("/" + EventsStoreAttributes.PARTITION_KEY)));
    TerraformResource dispatchedEventsContainer = container(stack,
        Utils.toTerraformName(stack.appPrefix, "dispatched-events"),
        Attributes.of(
            "name", names.dispatchedEventsStore(),
            "partition_key_paths", List.of("/eventId"),
            "unique_key", List.of(Attributes.of("paths", List.of("/eventId"))),
            "default_ttl", config.dispatchedEventsTtl));
    List<TerraformResource> readModels = new ArrayList<>();
    for (String readModel : config.readModels.keySet()) {
      readModels.add(container(stack, Utils.toTerraformName(readModel), Attributes.of(
          "name", names.forReadModel(readModel),
          "partition_key_paths", List.of("/id"))));
    }
    List<TerraformResource> result = new ArrayList<>();
    if (config.enableSubscriptions) {
      TerraformResource subscriptionsContainer = container(stack,
          Utils.toTerraformName(stack.appPrefix, "subscriptions-table"),
          Attributes.of(
              "name", names.subscriptionsStore(),
              "partition_key_paths", List.of("/" + SubscriptionsStoreAttributes.PARTITION_KEY),
              "default_ttl", -1));
      TerraformResource connectionsContainer = container(stack,
          Utils.toTerraformName(stack.appPrefix, "connections-table"),
          Attributes.of(
              "name", names.connectionsStore(),
              "partition_key_paths", List.of("/" + ConnectionsStoreAttributes.PARTITION_KEY),
              "default_ttl", -1));
      result.add(eventContainer);
      result.add(dispatchedEventsContainer);
      result.add(subscriptionsContainer);
      result.add(connectionsContainer);
      result.addAll(readModels);
      return result;
    }
    result.add(eventContainer);
    result.addAll(readModels);
    return result;
  }

  /**
   * {@code createDedupEventsContainer(stack, config)}: the event stream de-duplication container.
   *
   * @param stack the stack bag
   * @param config the configuration
   * @return the container
   * @throws IllegalStateException {@code Undefined cosmosdbDatabase resource} / {@code Undefined cosmosdbSqlDatabase
   *     resource}
   */
  public static TerraformResource createDedupEventsContainer(ApplicationSynthStack stack, BoosterConfig config) {
    checkDatabases(stack);
    return container(stack, Utils.toTerraformName(stack.appPrefix, "dedup-table"), Attributes.of(
        "name", config.resourceNames().eventsDedup(),
        "partition_key_paths", List.of("/" + DedupAttributes.PARTITION_KEY),
        "unique_key", List.of(Attributes.of("paths", List.of("/" + DedupAttributes.PARTITION_KEY))),
        "default_ttl", -1));
  }

  private static void checkDatabases(ApplicationSynthStack stack) {
    if (stack.cosmosdbDatabase == null) {
      throw new IllegalStateException("Undefined cosmosdbDatabase resource");
    }
    if (stack.cosmosdbSqlDatabase == null) {
      throw new IllegalStateException("Undefined cosmosdbSqlDatabase resource");
    }
  }

  private static TerraformResource container(ApplicationSynthStack stack, String id, Map<String, Object> specific) {
    Map<String, Object> attributes = Attributes.of(
        "name", specific.remove("name"),
        "resource_group_name", stack.cosmosdbDatabase.ref("resource_group_name"),
        "account_name", stack.cosmosdbDatabase.ref("name"),
        "database_name", stack.cosmosdbSqlDatabase.ref("name"),
        "partition_key_version", 2,
        "autoscale_settings", Attributes.of("max_throughput", Constants.maxContainerThroughput()));
    attributes.putAll(specific);
    return new TerraformResource(stack.terraformStack, id, "azurerm_cosmosdb_sql_container")
        .setAttributes(attributes)
        .provider(stack.azureProvider);
  }
}
