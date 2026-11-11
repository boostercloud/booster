package com.boostercloud.framework.provider.local.store;

/**
 * Options of {@link Datastore#update}, NeDB's {@code { multi, upsert, returnUpdatedDocs }} (all {@code false} by
 * default).
 *
 * @param multi update every matching document instead of the first one
 * @param upsert insert a document when nothing matches
 * @param returnUpdatedDocs return copies of the updated documents
 */
public record UpdateOptions(boolean multi, boolean upsert, boolean returnUpdatedDocs) {

  /** {@code {}}. */
  public static final UpdateOptions DEFAULTS = new UpdateOptions(false, false, false);

  /** {@code { multi: true }}. */
  public static final UpdateOptions MULTI = new UpdateOptions(true, false, false);
}
