package com.boostercloud.framework.provider.local.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of NeDB's {@code lib/cursor.js}: a query with optional sort, skip, limit and projection, executed with
 * {@link #exec()}.
 *
 * <ul>
 *   <li>Without a sort, documents come in candidate order ({@code _id} order unless an index supplied the candidates)
 *       and {@code skip}/{@code limit} are applied while scanning; with a sort, every match is collected, sorted
 *       stably with {@link Model#compareThings} (multiplied by each criterion's direction, criteria in insertion
 *       order) and then sliced ({@code slice(skip, skip + limit)}).
 *   <li>{@code skip} and {@code limit} follow JavaScript truthiness: {@code 0} (or {@code NaN}) means "none".
 *   <li>A projection either picks fields ({@code { a: 1, 'b.c': 1 }}) or omits them ({@code { a: 0 }}); {@code _id} is
 *       kept unless it is projected with {@code 0}. Mixing both kinds throws
 *       {@code Can't both keep and omit fields except for _id}.
 *   <li>Results are deep copies: modifying them does not affect the store.
 * </ul>
 */
public final class Cursor {

  private final Datastore datastore;
  private final Map<String, Object> query;
  private Map<String, Object> projection;
  private Map<String, Object> sort;
  private Number skip;
  private Number limit;

  Cursor(Datastore datastore, Map<String, Object> query, Map<String, Object> projection) {
    this.datastore = datastore;
    this.query = query == null ? new LinkedHashMap<>() : query;
    this.projection = projection;
  }

  /**
   * @param sortQuery field (dot path) to direction ({@code 1} ascending, {@code -1} descending), in priority order
   * @return this cursor
   */
  public Cursor sort(Map<String, ?> sortQuery) {
    this.sort = sortQuery == null ? null : new LinkedHashMap<>(sortQuery);
    return this;
  }

  /**
   * @param number documents to skip
   * @return this cursor
   */
  public Cursor skip(Number number) {
    this.skip = number;
    return this;
  }

  /**
   * @param number maximum number of documents
   * @return this cursor
   */
  public Cursor limit(Number number) {
    this.limit = number;
    return this;
  }

  /**
   * @param newProjection the projection
   * @return this cursor
   */
  public Cursor projection(Map<String, Object> newProjection) {
    this.projection = newProjection;
    return this;
  }

  /**
   * @return the matching documents (deep copies)
   */
  public List<Map<String, Object>> exec() {
    return datastore.execute(this);
  }

  Map<String, Object> query() {
    return query;
  }

  /** {@code _exec} over the candidates; the caller holds the datastore lock. */
  List<Map<String, Object>> execute(List<Map<String, Object>> candidates) {
    List<Map<String, Object>> result = new ArrayList<>();
    int added = 0;
    int skipped = 0;
    double skipValue = skip == null ? 0 : skip.doubleValue();
    double limitValue = limit == null ? 0 : limit.doubleValue();
    for (Map<String, Object> candidate : candidates) {
      if (Model.match(candidate, query)) {
        if (sort == null) {
          if (Js.truthy(skip) && skipValue > skipped) {
            skipped++;
          } else {
            result.add(candidate);
            added++;
            if (Js.truthy(limit) && limitValue <= added) {
              break;
            }
          }
        } else {
          result.add(candidate);
        }
      }
    }
    if (sort != null) {
      List<Map.Entry<String, Object>> criteria = new ArrayList<>(sort.entrySet());
      result.sort((a, b) -> {
        for (Map.Entry<String, Object> criterion : criteria) {
          double direction = Js.toNumber(criterion.getValue());
          double comparison = direction * Model.compareThings(
              Model.getDotValue(a, criterion.getKey()), Model.getDotValue(b, criterion.getKey()));
          if (Double.isNaN(comparison)) {
            return 0;
          }
          if (comparison != 0) {
            return comparison < 0 ? -1 : 1;
          }
        }
        return 0;
      });
      double effectiveLimit = Js.truthy(limit) ? limitValue : result.size();
      double effectiveSkip = Js.truthy(skip) ? skipValue : 0;
      result = Js.slice(result, effectiveSkip, effectiveSkip + effectiveLimit);
    }
    List<Map<String, Object>> projected = project(result);
    List<Map<String, Object>> copies = new ArrayList<>(projected.size());
    for (Map<String, Object> document : projected) {
      copies.add(Model.copyDocument(document));
    }
    return copies;
  }

  private List<Map<String, Object>> project(List<Map<String, Object>> candidates) {
    if (projection == null || projection.isEmpty()) {
      return candidates;
    }
    boolean keepId = !Model.strictEquals(projection.containsKey("_id") ? projection.get("_id") : Undefined.INSTANCE, 0);
    Map<String, Object> rest = new LinkedHashMap<>(projection);
    rest.remove("_id");
    Object action = Undefined.INSTANCE;
    for (Object value : rest.values()) {
      if (action != Undefined.INSTANCE && !Model.strictEquals(value, action)) {
        throw new NedbError("Can't both keep and omit fields except for _id");
      }
      action = value;
    }
    List<Map<String, Object>> result = new ArrayList<>(candidates.size());
    for (Map<String, Object> candidate : candidates) {
      Map<String, Object> toPush;
      if (Model.strictEquals(action, 1)) {
        Map<String, Object> set = new LinkedHashMap<>();
        for (String key : rest.keySet()) {
          Object value = Model.getDotValue(candidate, key);
          if (value != Undefined.INSTANCE) {
            set.put(key, value);
          }
        }
        toPush = Model.modify(new LinkedHashMap<>(), Map.of("$set", set));
      } else {
        Map<String, Object> unset = new LinkedHashMap<>();
        for (String key : rest.keySet()) {
          unset.put(key, true);
        }
        toPush = Model.modify(candidate, Map.of("$unset", unset));
      }
      if (keepId) {
        toPush.put("_id", candidate.get("_id"));
      } else {
        toPush.remove("_id");
      }
      result.add(toPush);
    }
    return result;
  }
}
