package com.boostercloud.framework.provider.local.store;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A datastore that runs an action right after a {@code find} whose query matches, once: it stands in for the
 * scheduler interleaving another thread exactly there.
 */
public final class HookedDatastore extends Datastore {

  private volatile Predicate<Map<String, Object>> trigger;
  private volatile Runnable action;

  /**
   * @param options the options
   */
  public HookedDatastore(DatastoreOptions options) {
    super(options);
  }

  /**
   * @param query selects the {@code find} after which the action runs
   * @param hook the action
   */
  public void afterNextFind(Predicate<Map<String, Object>> query, Runnable hook) {
    this.trigger = query;
    this.action = hook;
  }

  @Override
  List<Map<String, Object>> execute(Cursor cursor) {
    List<Map<String, Object>> result = super.execute(cursor);
    Runnable hook = action;
    if (hook != null && trigger.test(cursor.query())) {
      action = null;
      hook.run();
    }
    return result;
  }
}
