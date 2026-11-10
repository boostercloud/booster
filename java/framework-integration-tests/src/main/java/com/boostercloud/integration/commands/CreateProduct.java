package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.events.ProductCreated;
import com.boostercloud.integration.roles.Admin;
import com.boostercloud.integration.roles.UserWithEmail;
import java.util.Map;

/**
 * Creates a product ({@code src/commands/create-product.ts}). {@code productType} is a {@code ProductType} string,
 * exposed as {@code JSON} like the TypeScript string-literal union; missing details and type get the
 * {@code ProductCreated} defaults ({@code {}} and {@code 'Other'}).
 *
 * @param sku the SKU
 * @param displayName the name
 * @param description the description
 * @param priceInCents the price in cents
 * @param currency the currency
 * @param productID the product id ({@code UUID.generate()} when missing)
 * @param productDetails free-form details
 * @param productType one of the {@code ProductType} values
 */
@Command(authorize = {Admin.class, UserWithEmail.class})
public record CreateProduct(String sku, String displayName, String description, double priceInCents, String currency,
    @Nullable UUID productID, @Nullable Map<String, Object> productDetails, @Nullable Object productType) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(CreateProduct command, Register register) {
    UUID productID = command.productID() != null ? command.productID() : UUID.generate();
    register.events(new ProductCreated(productID, command.sku(), command.displayName(), command.description(),
        new Money(command.priceInCents(), command.currency()), command.productDetails(), command.productType()));
  }
}
