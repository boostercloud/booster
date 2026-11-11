package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Terraform output ({@code TerraformOutput}): {@code output.<logical id> = { value, description }}. Its logical id
 * is also recorded in the stack's {@code "//".outputs} metadata, nested by construct path below the stack.
 */
public class TerraformOutput extends TerraformElement {

  private final Object value;
  private final String description;

  /**
   * @param scope the scope (the TypeScript infrastructure uses the azurerm provider construct)
   * @param id the construct id
   * @param value the value (a string, possibly with references)
   * @param description the description, or {@code null}
   */
  public TerraformOutput(Construct scope, String id, Object value, String description) {
    super(scope, id);
    this.value = Objects.requireNonNull(value, "value");
    this.description = description;
  }

  /**
   * @return the value
   */
  public Object value() {
    return value;
  }

  /**
   * @return the description, or {@code null}
   */
  public String description() {
    return description;
  }

  @Override
  public Map<String, Object> toTerraform() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("value", DeepMerge.deepCopy(value));
    if (description != null) {
      body.put("description", description);
    }
    Map<String, Object> outputs = new LinkedHashMap<>();
    outputs.put(logicalId(), body);
    Map<String, Object> fragment = new LinkedHashMap<>();
    fragment.put("output", outputs);
    return fragment;
  }

  @Override
  public Map<String, Object> toMetadata() {
    List<String> components = new ArrayList<>();
    boolean belowStack = false;
    for (Construct construct : scopes()) {
      if (belowStack) {
        components.add(construct.id());
      }
      if (construct == stack()) {
        belowStack = true;
      }
    }
    Object nested = logicalId();
    for (int i = components.size() - 1; i >= 0; i--) {
      Map<String, Object> level = new LinkedHashMap<>();
      level.put(components.get(i), nested);
      nested = level;
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("outputs", nested);
    return metadata;
  }
}
