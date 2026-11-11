package com.boostercloud.framework.types.searcher;

import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fluent read model query builder, returned by {@code Booster.readModel(MyReadModel.class)}. Port of the TypeScript
 * {@code Searcher}: a <b>mutable</b> builder whose methods return the same instance, with the same "truthiness" rules
 * for the optional settings:
 *
 * <ul>
 *   <li>{@link #filter(Map)} always replaces the filter (no merge);
 *   <li>{@link #sortBy(Map)} and {@link #select(List)} ignore {@code null} (an empty list is kept);
 *   <li>{@link #limit(Integer)} ignores {@code null} and {@code 0};
 *   <li>{@link #afterCursor(Object)} ignores JavaScript-falsy values ({@code null}, {@code false}, {@code 0},
 *       {@code ""});
 *   <li>{@link #paginatedVersion(Boolean)} can only switch pagination on.
 * </ul>
 *
 * <pre>{@code
 * List<CartReadModel> carts = Booster.readModel(CartReadModel.class)
 *     .filter(Map.of("id", Map.of("eq", id)))
 *     .limit(10)
 *     .search();
 * }</pre>
 *
 * <p>With {@link #select(List)} the backend returns plain objects of the selected paths instead of {@code T} instances
 * (TypeScript types them {@code Partial<T>}). Read them with {@link #searchSelected()}, {@link #searchOneSelected()}
 * or {@link #searchSelectedPaginated()}; the typed {@link #search()}, {@link #searchOne()} and
 * {@link #searchPaginated()} refuse a selection instead of handing out maps typed as {@code T}:
 *
 * <pre>{@code
 * List<Map<String, Object>> skus = Booster.readModel(ProductReadModel.class)
 *     .select(List.of("id", "sku"))
 *     .searchSelected();
 * }</pre>
 *
 * @param <T> the read model type
 */
public final class Searcher<T> {

  private final Class<T> objectClass;
  private final SearcherFunction<T> searcherFunction;
  private final FinderByKeyFunction<T> finderByKeyFunction;
  private Integer limit;
  private Object afterCursor;
  private Map<String, Object> filters = new LinkedHashMap<>();
  private Map<String, Object> sortByList = new LinkedHashMap<>();
  private boolean paginatedVersion = false;
  private List<String> selectFor;

  /**
   * @param objectClass the searched class
   * @param searcherFunction the search backend
   * @param finderByKeyFunction the by-id backend
   */
  public Searcher(Class<T> objectClass, SearcherFunction<T> searcherFunction, FinderByKeyFunction<T> finderByKeyFunction) {
    this.objectClass = Objects.requireNonNull(objectClass, "objectClass");
    this.searcherFunction = Objects.requireNonNull(searcherFunction, "searcherFunction");
    this.finderByKeyFunction = Objects.requireNonNull(finderByKeyFunction, "finderByKeyFunction");
  }

  /**
   * Replaces the filter.
   *
   * @param filters a filter in {@link FilterFor} JSON shape
   * @return this searcher
   */
  public Searcher<T> filter(Map<String, Object> filters) {
    this.filters = filters;
    return this;
  }

  /**
   * Replaces the sort; {@code null} is ignored.
   *
   * @param sortBy a sort in {@link SortFor} JSON shape (key order is the sort precedence)
   * @return this searcher
   */
  public Searcher<T> sortBy(Map<String, Object> sortBy) {
    if (sortBy != null) {
      this.sortByList = sortBy;
    }
    return this;
  }

  /**
   * Selects only some properties; {@code null} is ignored (an empty list is kept). The search then returns plain
   * objects ({@code Map}s) of the selected paths, not {@code T} instances: run it with {@link #searchSelected()},
   * {@link #searchOneSelected()}, {@link #searchSelectedPaginated()} or {@link #searchRaw()} (the typed searches
   * refuse a selection).
   *
   * @param select projection paths ({@link ProjectionFor}, e.g. {@code "items[].id"})
   * @return this searcher
   */
  public Searcher<T> select(List<String> select) {
    if (select != null) {
      this.selectFor = select;
    }
    return this;
  }

  /**
   * Sets the maximum number of results; {@code null} and {@code 0} are ignored.
   *
   * @param limit the limit
   * @return this searcher
   */
  public Searcher<T> limit(Integer limit) {
    if (limit != null && limit != 0) {
      this.limit = limit;
    }
    return this;
  }

  /**
   * Continues after a cursor; JavaScript-falsy values are ignored.
   *
   * @param afterCursor the cursor of the previous page
   * @return this searcher
   */
  public Searcher<T> afterCursor(Object afterCursor) {
    if (isTruthy(afterCursor)) {
      this.afterCursor = afterCursor;
    }
    return this;
  }

  /**
   * Requests the paginated result shape; only {@code true} has an effect.
   *
   * @param paginatedVersion whether to paginate
   * @return this searcher
   */
  public Searcher<T> paginatedVersion(Boolean paginatedVersion) {
    if (Boolean.TRUE.equals(paginatedVersion)) {
      this.paginatedVersion = true;
    }
    return this;
  }

  /**
   * Finds by id, ignoring the builder state.
   *
   * @param id the id
   * @return the instance, or a list for sequenced read models
   * @deprecated [EOL v3] Use {@link #searchOne()} instead
   */
  @Deprecated
  public Object findById(UUID id) {
    return findById(id, null);
  }

  /**
   * Finds by id and sequence key, ignoring the builder state.
   *
   * @param id the id
   * @param sequenceKey the sequence key, or {@code null}
   * @return the instance, or a list for sequenced read models
   * @deprecated [EOL v3] Use {@link #searchOne()} instead
   */
  @Deprecated
  public Object findById(UUID id, SequenceKey sequenceKey) {
    return finderByKeyFunction.find(objectClass, id, sequenceKey);
  }

  /**
   * Runs the search with {@code limit = 1} and pagination off (whatever the builder says) and returns the first
   * result.
   *
   * @return the first result, if any
   * @throws IllegalStateException when {@link #select(List)} was called: use {@link #searchOneSelected()}
   */
  @SuppressWarnings("unchecked")
  public Optional<T> searchOne() {
    refuseSelection("searchOne()", "searchOneSelected()");
    return Optional.ofNullable((T) firstResult());
  }

  /**
   * Runs the search with the builder state and returns the items (when the backend returns a paginated result,
   * its {@code items}).
   *
   * @return the results
   * @throws IllegalStateException when {@link #select(List)} was called: use {@link #searchSelected()}
   */
  @SuppressWarnings("unchecked")
  public List<T> search() {
    refuseSelection("search()", "searchSelected()");
    return (List<T>) items(searchRaw());
  }

  /**
   * Runs the search with pagination on and returns the page ({@code { items, count, cursor }}).
   *
   * @return the page
   * @throws IllegalStateException when {@link #select(List)} was called: use {@link #searchSelectedPaginated()}
   */
  @SuppressWarnings("unchecked")
  public ReadModelListResult<T> searchPaginated() {
    refuseSelection("searchPaginated()", "searchSelectedPaginated()");
    return (ReadModelListResult<T>) page();
  }

  /**
   * {@link #search()} for a search with {@link #select(List)}: the plain objects of the selected paths.
   *
   * @return the results, one ordered map per item
   * @throws IllegalStateException when no selection was set
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> searchSelected() {
    requireSelection("searchSelected()");
    return (List<Map<String, Object>>) items(searchRaw());
  }

  /**
   * {@link #searchOne()} for a search with {@link #select(List)}.
   *
   * @return the plain object of the first result, if any
   * @throws IllegalStateException when no selection was set
   */
  @SuppressWarnings("unchecked")
  public Optional<Map<String, Object>> searchOneSelected() {
    requireSelection("searchOneSelected()");
    return Optional.ofNullable((Map<String, Object>) firstResult());
  }

  /**
   * {@link #searchPaginated()} for a search with {@link #select(List)}.
   *
   * @return the page of plain objects
   * @throws IllegalStateException when no selection was set
   */
  @SuppressWarnings("unchecked")
  public ReadModelListResult<Map<String, Object>> searchSelectedPaginated() {
    requireSelection("searchSelectedPaginated()");
    return (ReadModelListResult<Map<String, Object>>) page();
  }

  private Object firstResult() {
    Object result = searcherFunction.search(objectClass, filters, sortByList, 1, afterCursor, false, selectFor);
    if (result instanceof List<?> list && !list.isEmpty()) {
      return list.get(0);
    }
    return null;
  }

  private static List<?> items(Object result) {
    if (result instanceof ReadModelListResult<?> page) {
      return page.items();
    }
    return result == null ? List.of() : (List<?>) result;
  }

  private ReadModelListResult<?> page() {
    paginatedVersion = true;
    Object result = searchRaw();
    if (result instanceof ReadModelListResult<?> page) {
      return page;
    }
    return ReadModelListResult.of(result == null ? List.of() : (List<?>) result);
  }

  /** TypeScript types the results {@code Partial<T>}; a cast to {@code T} would fail later, in the caller. */
  private void refuseSelection(String method, String selectedMethod) {
    if (selectFor != null) {
      throw new IllegalStateException(method + " returns " + objectClass.getSimpleName() + " instances, but select(...)"
          + " makes the search return plain objects of the selected paths: call " + selectedMethod
          + (method.equals("search()") ? " (or searchRaw())" : "") + " instead");
    }
  }

  private void requireSelection(String method) {
    if (selectFor == null) {
      throw new IllegalStateException(method + " needs select(...): without a selection the search returns "
          + objectClass.getSimpleName() + " instances");
    }
  }

  /**
   * Runs the search exactly like TypeScript's {@code search()}: the backend result as is.
   *
   * @return a {@code List<T>} or a {@code ReadModelListResult<T>}
   */
  public Object searchRaw() {
    return searcherFunction.search(objectClass, filters, sortByList, limit, afterCursor, paginatedVersion, selectFor);
  }

  /**
   * @return a snapshot of the builder state (the private fields TypeScript tests inspect)
   */
  public State state() {
    return new State(objectClass, filters, sortByList, limit, afterCursor, paginatedVersion, selectFor);
  }

  /**
   * The builder state.
   *
   * @param objectClass the searched class
   * @param filters the filter
   * @param sortBy the sort
   * @param limit the limit, or {@code null}
   * @param afterCursor the cursor, or {@code null}
   * @param paginatedVersion whether pagination is on
   * @param select the projection, or {@code null}
   */
  public record State(
      Class<?> objectClass,
      Map<String, Object> filters,
      Map<String, Object> sortBy,
      Integer limit,
      Object afterCursor,
      boolean paginatedVersion,
      List<String> select) {}

  private static boolean isTruthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean bool -> bool;
      case String string -> !string.isEmpty();
      case Double number -> number != 0 && !number.isNaN();
      case Float number -> number != 0 && !number.isNaN();
      case Number number -> number.doubleValue() != 0;
      default -> true;
    };
  }
}
