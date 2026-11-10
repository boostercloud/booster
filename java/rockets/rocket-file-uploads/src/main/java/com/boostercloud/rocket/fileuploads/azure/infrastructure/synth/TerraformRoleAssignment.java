package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;

/**
 * Port of {@code synth/terraform-role-assignment.ts}.
 */
public final class TerraformRoleAssignment {

  /** The role that reads and writes blobs (and requests user delegation keys). */
  public static final String STORAGE_BLOB_DATA_CONTRIBUTOR = "Storage Blob Data Contributor";
  /** The role that reads the account (and lists its keys). */
  public static final String READER_AND_DATA_ACCESS = "Reader and Data Access";

  private TerraformRoleAssignment() {}

  /**
   * {@code TerraformRoleAssignment.build(...)}: grants {@value #STORAGE_BLOB_DATA_CONTRIBUTOR} and
   * {@value #READER_AND_DATA_ACCESS} on a storage account to the system-assigned identity of a function app
   * ({@code principal_id = ${<function app>.identity.0.principal_id}}). The construct ids are
   * {@code toTerraformName(<storage name><function app suffix>, 'rfrlb' | 'rfrlr')}, where the suffix is the function
   * app name without the resource group name (TypeScript uses unresolved tokens, so its ids are opaque), made unique
   * with {@link ConstructIds} when two storage names end alike.
   *
   * @param stack the stack bag
   * @param utils the rocket utilities
   * @param rocketStorageAccount the storage account
   * @param storageAccountName its name
   * @param functionAppResource the function app
   * @return the two role assignments (contributor first)
   * @throws IllegalStateException {@code FunctionApp identity not found} when the function app has no identity
   */
  public static List<TerraformResource> build(ApplicationSynthStack stack, RocketUtils utils,
      TerraformResource rocketStorageAccount, String storageAccountName, TerraformResource functionAppResource) {
    if (functionAppResource.attribute("identity") == null
        && !functionAppResource.rawOverrides().containsKey("identity")) {
      throw new IllegalStateException("FunctionApp identity not found");
    }
    String firstIdentity = functionAppResource.ref("identity.0.principal_id");
    String idBase = storageAccountName + functionAppKey(stack, functionAppResource);
    TerraformResource blobContributorRole = new TerraformResource(stack.terraformStack,
        ConstructIds.unique(stack.terraformStack, utils, idBase, "rfrlb"), "azurerm_role_assignment")
        .setAttributes(Attributes.of(
            "scope", rocketStorageAccount.ref("id"),
            "role_definition_name", STORAGE_BLOB_DATA_CONTRIBUTOR,
            "principal_id", firstIdentity))
        .provider(stack.azureProvider);
    TerraformResource readerAndDataRole = new TerraformResource(stack.terraformStack,
        ConstructIds.unique(stack.terraformStack, utils, idBase, "rfrlr"), "azurerm_role_assignment")
        .setAttributes(Attributes.of(
            "scope", rocketStorageAccount.ref("id"),
            "role_definition_name", READER_AND_DATA_ACCESS,
            "principal_id", firstIdentity))
        .provider(stack.azureProvider);
    return List.of(blobContributorRole, readerAndDataRole);
  }

  /** The function app name without the resource group name ({@code filesfunc}, {@code func}). */
  private static String functionAppKey(ApplicationSynthStack stack, TerraformResource functionApp) {
    Object name = functionApp.attribute("name");
    String text = name instanceof String value ? value : functionApp.id();
    return text.startsWith(stack.resourceGroupName) ? text.substring(stack.resourceGroupName.length()) : text;
  }
}
