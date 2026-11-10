package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.util.Map;

/**
 * The output of {@code az resource show/list} ({@code Resource} of {@code types/resource.ts}); unknown properties
 * are ignored.
 *
 * @param id the resource id
 * @param location the location
 * @param managedBy the managing resource, or {@code null}
 * @param name the resource name
 * @param type the resource type
 * @param properties the resource properties, or {@code null}
 */
public record Resource(
    String id, String location, String managedBy, String name, String type, Map<String, Object> properties) {}
