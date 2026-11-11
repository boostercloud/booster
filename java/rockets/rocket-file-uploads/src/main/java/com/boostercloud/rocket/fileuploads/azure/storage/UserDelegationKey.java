package com.boostercloud.rocket.fileuploads.azure.storage;

import java.time.Instant;

/**
 * A user delegation key ({@code UserDelegationKey} of {@code @azure/storage-blob}): the answer of
 * {@code Get User Delegation Key}, used to sign user delegation SAS tokens.
 *
 * @param signedObjectId {@code SignedOid}: the object id of the identity that requested the key
 * @param signedTenantId {@code SignedTid}: its tenant
 * @param signedStartsOn {@code SignedStart}
 * @param signedExpiresOn {@code SignedExpiry}
 * @param signedService {@code SignedService} ({@code b})
 * @param signedVersion {@code SignedVersion}: the REST version the key was requested with
 * @param value {@code Value}: the base64 key
 */
public record UserDelegationKey(String signedObjectId, String signedTenantId, Instant signedStartsOn,
    Instant signedExpiresOn, String signedService, String signedVersion, String value) {}
