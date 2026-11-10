package com.boostercloud.integration.entities;

import java.util.List;

/**
 * The values of TypeScript's {@code type ProductType = 'Furniture' | 'Electronics' | 'Books' | 'Clothing' | 'Other'}
 * ({@code src/entities/product.ts}).
 *
 * <p>TypeScript maps that string-literal union to the GraphQL {@code JSON} scalar (the tests send
 * {@code $productType: JSON}). Java has no type aliases and a Java {@code enum} would be a GraphQL enum, so the
 * properties holding a product type are declared {@code Object} (GraphQL {@code JSON}) and hold one of these strings.
 */
public final class ProductType {

  /** {@code 'Furniture'}. */
  public static final String FURNITURE = "Furniture";
  /** {@code 'Electronics'}. */
  public static final String ELECTRONICS = "Electronics";
  /** {@code 'Books'}. */
  public static final String BOOKS = "Books";
  /** {@code 'Clothing'}. */
  public static final String CLOTHING = "Clothing";
  /** {@code 'Other'}, the default. */
  public static final String OTHER = "Other";
  /** Every value. */
  public static final List<String> VALUES = List.of(FURNITURE, ELECTRONICS, BOOKS, CLOTHING, OTHER);

  private ProductType() {}

  /**
   * TypeScript's default parameter {@code productType: ProductType = 'Other'}: applies when the value is missing.
   *
   * @param productType the given value, or {@code null}
   * @return the value, or {@link #OTHER}
   */
  public static Object orDefault(Object productType) {
    return productType == null ? OTHER : productType;
  }
}
