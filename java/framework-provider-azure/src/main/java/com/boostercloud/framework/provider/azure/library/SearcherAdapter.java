package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.helpers.QueryHelper;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import java.util.Map;

/** Port of {@code library/searcher-adapter.ts}: read model searches on the read model's container. */
public final class SearcherAdapter {

  private SearcherAdapter() {}

  /**
   * {@code searchReadModel}: {@link QueryHelper#search} on {@code <app>-app-<ReadModel>}, with the arguments reordered
   * ({@code sortBy} goes after {@code paginatedVersion}).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param readModelName the read model class name
   * @param filters the filter
   * @param sortBy the sort, or {@code null}
   * @param limit the limit, or {@code null}
   * @param afterCursor the cursor, or {@code null}
   * @param paginatedVersion whether the paginated shape is wanted ({@code null} = {@code false})
   * @param select the projection paths, or {@code null} for {@code *}
   * @return a list, or a {@code ReadModelListResult} when paginated
   */
  public static Object searchReadModel(
      CosmosClient cosmosDb,
      BoosterConfig config,
      String readModelName,
      Map<String, ?> filters,
      Map<String, ?> sortBy,
      Integer limit,
      Map<String, ?> afterCursor,
      Boolean paginatedVersion,
      List<String> select) {
    return QueryHelper.search(cosmosDb, config, config.resourceNames().forReadModel(readModelName), filters, limit,
        afterCursor, paginatedVersion, sortBy, select);
  }
}
