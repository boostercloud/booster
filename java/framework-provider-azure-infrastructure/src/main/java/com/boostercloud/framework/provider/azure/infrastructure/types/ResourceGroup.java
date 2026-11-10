package com.boostercloud.framework.provider.azure.infrastructure.types;

/**
 * The output of {@code az group show} ({@code ResourceGroup} of {@code types/resource-group.ts}); unknown properties
 * are ignored.
 *
 * @param id the resource id
 * @param location the location
 * @param managedBy the managing resource, or {@code null}
 * @param name the resource group name
 */
public record ResourceGroup(String id, String location, String managedBy, String name) {}
