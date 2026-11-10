package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A construct that contributes to the synthesized Terraform JSON ({@code TerraformElement} of CDK for Terraform):
 * resources, data sources, providers and outputs. Its logical id is allocated by the enclosing {@link TerraformStack}.
 */
public abstract class TerraformElement extends Construct {

  private String overriddenLogicalId;

  /**
   * @param scope the parent construct (a stack, or another element for nested outputs)
   * @param id the construct id
   */
  protected TerraformElement(Construct scope, String id) {
    super(scope, id);
    stack();
  }

  /**
   * @return the stack this element belongs to
   * @throws IllegalStateException when the element is not inside a stack
   */
  public TerraformStack stack() {
    for (Construct current = scope(); current != null; current = current.scope()) {
      if (current instanceof TerraformStack stack) {
        return stack;
      }
    }
    throw new IllegalStateException("No stack could be identified for the construct at path '" + path() + "'");
  }

  /**
   * The Terraform logical name ({@code friendlyUniqueId}): {@link LogicalIds#makeUniqueId} of the construct ids
   * below the stack, unless overridden.
   *
   * @return the logical id
   */
  public String logicalId() {
    if (overriddenLogicalId != null) {
      return overriddenLogicalId;
    }
    List<String> components = new ArrayList<>();
    boolean belowStack = false;
    TerraformStack stack = stack();
    for (Construct construct : scopes()) {
      if (belowStack) {
        components.add(construct.id());
      }
      if (construct == stack) {
        belowStack = true;
      }
    }
    return LogicalIds.makeUniqueId(components, true);
  }

  /**
   * {@code overrideLogicalId(newLogicalId)}.
   *
   * @param newLogicalId the logical id to use
   */
  public void overrideLogicalId(String newLogicalId) {
    this.overriddenLogicalId = newLogicalId;
  }

  /**
   * @return the JSON fragment merged into the stack document
   */
  public abstract Map<String, Object> toTerraform();

  /**
   * @return the fragment merged into the stack's {@code "//"} metadata (empty by default)
   */
  public Map<String, Object> toMetadata() {
    return Map.of();
  }
}
