package com.boostercloud.framework.provider.local.store;

/**
 * Result of {@link Datastore#update}, NeDB's {@code { numAffected, affectedDocuments, upsert }}.
 *
 * @param numAffected number of updated (or inserted) documents
 * @param affectedDocuments with {@code returnUpdatedDocs}: the updated document (one map) or, with {@code multi}, the
 *     list of updated documents; the inserted document for an upsert; otherwise {@code null}
 * @param upsert whether a document was inserted
 */
public record UpdateResult(int numAffected, Object affectedDocuments, boolean upsert) {}
