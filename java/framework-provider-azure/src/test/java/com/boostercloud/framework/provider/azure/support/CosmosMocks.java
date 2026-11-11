package com.boostercloud.framework.provider.azure.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.BatchResponse;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosDatabase;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItem;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItems;
import com.boostercloud.framework.provider.azure.cosmos.CosmosQueryIterator;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;

/**
 * The Java counterpart of the sinon stub the TypeScript tests build with {@code createStubInstance(CosmosClient, ...)}:
 * one database, one container, one items object, one item and one query iterator, all returning empty results.
 */
public final class CosmosMocks {
  public final CosmosClient client = mock(CosmosClient.class);
  public final CosmosDatabase database = mock(CosmosDatabase.class);
  public final CosmosContainer container = mock(CosmosContainer.class);
  public final CosmosItems items = mock(CosmosItems.class);
  public final CosmosItem item = mock(CosmosItem.class);
  public final CosmosQueryIterator iterator = mock(CosmosQueryIterator.class);

  public CosmosMocks() {
    when(client.database(anyString())).thenReturn(database);
    when(database.container(anyString())).thenReturn(container);
    when(database.url()).thenReturn("dbs/new-booster-app-app");
    when(container.items()).thenReturn(items);
    when(container.item(anyString(), any())).thenReturn(item);
    when(items.query(any(SqlQuerySpec.class))).thenReturn(iterator);
    when(items.query(any(SqlQuerySpec.class), any(FeedOptions.class))).thenReturn(iterator);
    when(items.create(any())).thenReturn(ItemResponse.of(201, Map.of()));
    when(items.upsert(any(), any(RequestOptions.class))).thenReturn(ItemResponse.of(200, Map.of()));
    when(items.batch(any(), any(), any())).thenReturn(new BatchResponse(Map.of(), List.of(), 200, null));
    when(item.read()).thenReturn(ItemResponse.of(404, null));
    when(item.replace(any())).thenReturn(ItemResponse.of(200, Map.of()));
    when(item.delete()).thenReturn(ItemResponse.of(204, null));
    when(iterator.fetchAll()).thenReturn(FeedResponse.of(List.of()));
    when(iterator.fetchNext()).thenReturn(FeedResponse.of(List.of()));
  }

  /** @return the only query spec sent through {@code items.query(spec)} */
  public SqlQuerySpec querySpec() {
    ArgumentCaptor<SqlQuerySpec> captor = ArgumentCaptor.forClass(SqlQuerySpec.class);
    verify(items).query(captor.capture());
    return captor.getValue();
  }

  /** @return results of the given rows */
  public void returnRows(List<Object> rows) {
    when(iterator.fetchAll()).thenReturn(FeedResponse.of(rows));
  }

  /**
   * An ordered map literal ({@code { k1: v1, k2: v2 }}), with {@code null} values allowed.
   *
   * @param keyValues alternating keys and values
   * @return the map
   */
  public static Map<String, Object> obj(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }
}
