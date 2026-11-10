package com.boostercloud.framework.provider.azure.infrastructure.terraform;

/**
 * A Terraform data source ({@code TerraformDataSource}): written under {@code "data"}, referenced as
 * {@code ${data.<type>.<logical id>.<attribute>}}.
 */
public class TerraformDataSource extends TerraformResource {

  /**
   * @param scope the stack
   * @param id the construct id
   * @param terraformResourceType the data source type, e.g. {@code azurerm_public_ip}
   */
  public TerraformDataSource(Construct scope, String id, String terraformResourceType) {
    super(scope, id, terraformResourceType);
  }

  @Override
  public String fqn() {
    return "data." + terraformResourceType() + "." + logicalId();
  }

  @Override
  protected String section() {
    return "data";
  }
}
