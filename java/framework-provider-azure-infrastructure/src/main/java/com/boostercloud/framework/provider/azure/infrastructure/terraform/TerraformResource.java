package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Terraform resource ({@code TerraformResource} of CDK for Terraform plus the typed {@code @cdktn/provider-azurerm}
 * classes, which here are one generic class): a type such as {@code azurerm_resource_group}, attributes written with
 * their Terraform (snake_case) names, and the meta-arguments {@code depends_on}, {@code provider} and
 * {@code lifecycle}.
 *
 * <p>As in CDK for Terraform, reading an attribute of another resource yields a reference:
 * {@code resourceGroup.ref("location")} is {@code ${azurerm_resource_group.<logical id>.location}}, which can be
 * embedded in larger strings ({@code "https://" + functionApp.ref("name") + ".azurewebsites.net"}). Attribute values are
 * raw values: strings (possibly containing references), numbers, booleans, {@code Map}s for single nested blocks and
 * {@code List}s of maps for repeated blocks; {@code null} means "not set" and is not written.
 */
public class TerraformResource extends TerraformElement {

  private final String terraformResourceType;
  private final Map<String, Object> attributes = new LinkedHashMap<>();
  private final List<TerraformResource> dependsOn = new ArrayList<>();
  private final Map<String, Object> rawOverrides = new LinkedHashMap<>();
  private TerraformProvider provider;
  private Map<String, Object> lifecycle;

  /**
   * @param scope the stack
   * @param id the construct id (the Terraform logical name of a direct child of the stack)
   * @param terraformResourceType the resource type, e.g. {@code azurerm_resource_group}
   */
  public TerraformResource(Construct scope, String id, String terraformResourceType) {
    super(scope, id);
    this.terraformResourceType = Objects.requireNonNull(terraformResourceType, "terraformResourceType");
  }

  /**
   * @return the Terraform resource type
   */
  public String terraformResourceType() {
    return terraformResourceType;
  }

  /**
   * @return {@code <type>.<logical id>}, the address used in references and {@code depends_on}
   */
  public String fqn() {
    return terraformResourceType + "." + logicalId();
  }

  /**
   * A reference to an attribute of this resource ({@code getStringAttribute(name)} in CDK for Terraform).
   *
   * @param attribute the Terraform attribute path, e.g. {@code name} or {@code identity.0.principal_id}
   * @return {@code ${<fqn>.<attribute>}}
   */
  public String ref(String attribute) {
    return "${" + fqn() + "." + attribute + "}";
  }

  /**
   * @return a live view of the attributes (snake_case names, insertion order)
   */
  public Map<String, Object> attributes() {
    return attributes;
  }

  /**
   * @param name the attribute name
   * @return the value given to the attribute, or {@code null}
   */
  public Object attribute(String name) {
    return attributes.get(name);
  }

  /**
   * Sets (or, with {@code null}, unsets) an attribute; the TypeScript setters ({@code functionApp.appSettings = …}).
   *
   * @param name the attribute name
   * @param value the raw value
   * @return this resource
   */
  public TerraformResource setAttribute(String name, Object value) {
    if (value == null) {
      attributes.remove(name);
    } else {
      attributes.put(name, value);
    }
    return this;
  }

  /**
   * Sets several attributes, in order (null values skipped).
   *
   * @param values the attributes
   * @return this resource
   */
  public TerraformResource setAttributes(Map<String, ?> values) {
    values.forEach(this::setAttribute);
    return this;
  }

  /**
   * {@code dependsOn: [...]}.
   *
   * @param resources the resources this one depends on
   * @return this resource
   */
  public TerraformResource dependsOn(TerraformResource... resources) {
    for (TerraformResource resource : resources) {
      dependsOn.add(Objects.requireNonNull(resource, "resource"));
    }
    return this;
  }

  /**
   * @return the resources this one depends on
   */
  public List<TerraformResource> dependencies() {
    return List.copyOf(dependsOn);
  }

  /**
   * {@code provider: azureProvider}.
   *
   * @param provider the provider, or {@code null} for the default one
   * @return this resource
   */
  public TerraformResource provider(TerraformProvider provider) {
    this.provider = provider;
    return this;
  }

  /**
   * @return the explicit provider, or {@code null}
   */
  public TerraformProvider provider() {
    return provider;
  }

  /**
   * {@code lifecycle: { ignoreChanges: [...] }}.
   *
   * @param ignoreChanges the attributes Terraform must not update
   * @return this resource
   */
  public TerraformResource ignoreChanges(String... ignoreChanges) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("ignore_changes", List.of(ignoreChanges));
    this.lifecycle = value;
    return this;
  }

  /**
   * @return the lifecycle block, or {@code null}
   */
  public Map<String, Object> lifecycle() {
    return lifecycle;
  }

  /**
   * {@code addOverride(path, value)}: writes a raw value into the synthesized JSON of this resource, after the
   * attributes (see {@link DeepMerge}). Rockets use it to patch the application gateway.
   *
   * @param path a dot-separated path of Terraform names, e.g. {@code url_path_map.0.path_rule}
   * @param value the raw value
   */
  public void addOverride(String path, Object value) {
    DeepMerge.addOverride(rawOverrides, path, value);
  }

  /**
   * @return the raw overrides tree (a live view)
   */
  public Map<String, Object> rawOverrides() {
    return rawOverrides;
  }

  /**
   * @return {@code "resource"}, the top-level section of this element
   */
  protected String section() {
    return "resource";
  }

  /**
   * @return the body of this resource: attributes, meta-arguments, overrides and the {@code "//"} metadata
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> body() {
    Map<String, Object> body = (Map<String, Object>) DeepMerge.deepCopy(attributes);
    if (!dependsOn.isEmpty()) {
      List<String> addresses = new ArrayList<>();
      for (TerraformResource dependency : dependsOn) {
        addresses.add(dependency.fqn());
      }
      body.put("depends_on", addresses);
    }
    if (provider != null) {
      body.put("provider", provider.fqn());
    }
    if (lifecycle != null) {
      body.put("lifecycle", DeepMerge.deepCopy(lifecycle));
    }
    DeepMerge.deepMerge(body, rawOverrides);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("path", path());
    metadata.put("uniqueId", logicalId());
    Map<String, Object> comment = new LinkedHashMap<>();
    comment.put("metadata", metadata);
    body.put("//", comment);
    return body;
  }

  @Override
  public Map<String, Object> toTerraform() {
    Map<String, Object> byId = new LinkedHashMap<>();
    byId.put(logicalId(), body());
    Map<String, Object> byType = new LinkedHashMap<>();
    byType.put(terraformResourceType, byId);
    Map<String, Object> fragment = new LinkedHashMap<>();
    fragment.put(section(), byType);
    return fragment;
  }
}
