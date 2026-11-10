package com.boostercloud.integration.events;

/**
 * Why a product was updated ({@code export enum ProductUpdateReason} of {@code src/events/product-updated.ts}); the
 * constant names equal the TypeScript string values.
 */
public enum ProductUpdateReason {
  /** {@code 'CatalogChange'}. */
  CatalogChange,
  /** {@code 'FixDescription'}. */
  FixDescription,
  /** {@code 'FixName'}. */
  FixName
}
