package com.boostercloud.framework.types.searcher;

import java.util.List;
import java.util.Map;

/**
 * The search backend of a {@link Searcher}. Port of {@code SearcherFunction}; it always receives the seven arguments
 * (trailing ones may be {@code null}).
 *
 * @param <T> the searched type
 */
@FunctionalInterface
public interface SearcherFunction<T> {
  /**
   * @param objectClass the searched class
   * @param filters the filter ({@link FilterFor} JSON shape)
   * @param sortBy the sort ({@link SortFor} JSON shape, ordered)
   * @param limit maximum number of results, or {@code null}
   * @param afterCursor opaque cursor, or {@code null}
   * @param paginatedVersion whether the paginated shape is requested
   * @param select projection paths, or {@code null}
   * @return a {@code List<T>} when not paginated, a {@code ReadModelListResult<T>} when paginated
   */
  Object search(
      Class<T> objectClass,
      Map<String, Object> filters,
      Map<String, Object> sortBy,
      Integer limit,
      Object afterCursor,
      boolean paginatedVersion,
      List<String> select);
}
