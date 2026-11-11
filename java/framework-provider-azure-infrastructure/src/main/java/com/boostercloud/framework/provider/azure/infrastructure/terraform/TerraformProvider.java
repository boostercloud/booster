package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A provider configuration ({@code TerraformProvider}): writes its attributes under {@code provider.<type>} (a list of
 * configurations, as CDK for Terraform does) and its source and version constraint under
 * {@code terraform.required_providers.<type>}. Resources that set it explicitly get {@code "provider": "<type>"}.
 *
 * <p>The provider is also a construct scope: the TypeScript infrastructure creates its outputs inside the azurerm
 * provider construct, which is why their logical ids carry a hash.
 */
public class TerraformProvider extends TerraformElement {

  private final String terraformResourceType;
  private final String source;
  private final String versionConstraint;
  private final Map<String, Object> attributes = new LinkedHashMap<>();

  /**
   * @param scope the stack
   * @param id the construct id ({@code azureFeature} for azurerm)
   * @param terraformResourceType the provider name ({@code azurerm})
   * @param source the source written to {@code required_providers}
   * @param versionConstraint the version constraint written to {@code required_providers}
   */
  public TerraformProvider(
      Construct scope, String id, String terraformResourceType, String source, String versionConstraint) {
    super(scope, id);
    this.terraformResourceType = Objects.requireNonNull(terraformResourceType, "terraformResourceType");
    this.source = Objects.requireNonNull(source, "source");
    this.versionConstraint = Objects.requireNonNull(versionConstraint, "versionConstraint");
  }

  @Override
  protected String typeName() {
    return terraformResourceType.substring(0, 1).toUpperCase(Locale.ROOT) + terraformResourceType.substring(1)
        + "Provider";
  }

  /**
   * @return the provider name
   */
  public String terraformResourceType() {
    return terraformResourceType;
  }

  /**
   * @return the provider source
   */
  public String source() {
    return source;
  }

  /**
   * @return the version constraint
   */
  public String versionConstraint() {
    return versionConstraint;
  }

  /**
   * @return {@code <type>} (no alias), the value of the {@code provider} meta-argument
   */
  public String fqn() {
    return terraformResourceType;
  }

  /**
   * @param name the Terraform argument name
   * @param value the raw value ({@code null} unsets it)
   * @return this provider
   */
  public TerraformProvider setAttribute(String name, Object value) {
    if (value == null) {
      attributes.remove(name);
    } else {
      attributes.put(name, value);
    }
    return this;
  }

  /**
   * @return a live view of the provider arguments
   */
  public Map<String, Object> attributes() {
    return attributes;
  }

  @Override
  public Map<String, Object> toTerraform() {
    Map<String, Object> requirement = new LinkedHashMap<>();
    requirement.put("source", source);
    requirement.put("version", versionConstraint);
    Map<String, Object> requiredProviders = new LinkedHashMap<>();
    requiredProviders.put(terraformResourceType, requirement);
    Map<String, Object> terraform = new LinkedHashMap<>();
    terraform.put("required_providers", requiredProviders);

    Map<String, Object> providers = new LinkedHashMap<>();
    providers.put(terraformResourceType, List.of(DeepMerge.deepCopy(attributes)));

    Map<String, Object> fragment = new LinkedHashMap<>();
    fragment.put("terraform", terraform);
    fragment.put("provider", providers);
    return fragment;
  }
}
