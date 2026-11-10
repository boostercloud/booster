package com.boostercloud.integration.queries;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.integration.readmodels.BookReadModel;
import com.boostercloud.integration.readmodels.MediaValue;
import com.boostercloud.integration.readmodels.MovieReadModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Books and movies whose title contains a word ({@code src/queries/search-media.ts}); the result lists a GraphQL
 * union ({@link MediaValue}: {@code BookReadModel | MovieReadModel}).
 *
 * @param searchword the word
 */
@Query(authorize = All.class)
public record SearchMedia(String searchword) {

  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the books, then the movies
   */
  public static SearchResult handle(SearchMedia query, QueryInfo queryInfo) {
    Map<String, Object> byTitle = Map.of("title", Map.of("contains", query.searchword()));
    List<List<? extends MediaValue>> found = Promises.runAll(List.<Callable<List<? extends MediaValue>>>of(
        () -> Booster.readModel(BookReadModel.class).filter(byTitle).search(),
        () -> Booster.readModel(MovieReadModel.class).filter(byTitle).search()));
    List<MediaValue> response = new ArrayList<>(found.get(0));
    response.addAll(found.get(1));
    return new SearchResult(response);
  }

  /**
   * The result of {@link SearchMedia} (the module-private {@code class SearchResult} of the TypeScript file).
   *
   * @param results the books and movies found
   */
  public record SearchResult(List<MediaValue> results) {}
}
