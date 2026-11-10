package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import com.boostercloud.framework.provider.azure.infrastructure.helper.JsonText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Terraform stack ({@code TerraformStack}): the root of the constructs that make up one {@code cdk.tf.json}. Its
 * document has the CDK for Terraform layout:
 *
 * <pre>{@code
 * {
 *   "//": { "metadata": { "backend": "local", "stackName": "<id>", "version": "<cdktn version>" }, "outputs": {...} },
 *   "data": {...}, "output": {...}, "provider": {...}, "resource": {...},
 *   "terraform": { "backend": { "local": { "path": "<project>/terraform.<id>.tfstate" } }, "required_providers": {...} }
 * }
 * }</pre>
 *
 * written with every object's keys sorted so the output is deterministic. The local backend keeps the state in the
 * project root, as CDK for Terraform's default backend does.
 */
public class TerraformStack extends Construct {

  /** The CDK for Terraform version whose JSON layout this synthesis reproduces ({@code cdktn} 0.25.0). */
  public static final String CDKTN_VERSION = "0.25.0";

  private final TerraformApp app;

  /**
   * @param app the application
   * @param id the stack id (the TypeScript infrastructure uses {@code appName + environmentName})
   */
  public TerraformStack(TerraformApp app, String id) {
    super(app, id);
    this.app = app;
  }

  /**
   * @return the application this stack belongs to
   */
  public TerraformApp app() {
    return app;
  }

  /**
   * @return the absolute path of the local Terraform state ({@code <project>/terraform.<id>.tfstate})
   */
  public String backendPath() {
    return app.projectRoot().resolve("terraform." + id() + ".tfstate").toAbsolutePath().normalize().toString();
  }

  /**
   * @return every Terraform element of the stack, depth first in creation order
   */
  public List<TerraformElement> elements() {
    List<TerraformElement> elements = new ArrayList<>();
    for (Construct construct : findAll()) {
      if (construct instanceof TerraformElement element) {
        elements.add(element);
      }
    }
    return elements;
  }

  /**
   * @param <T> the element type
   * @param type an element class
   * @return the elements of that class, in creation order
   */
  public <T extends TerraformElement> List<T> elements(Class<T> type) {
    List<T> matches = new ArrayList<>();
    for (TerraformElement element : elements()) {
      if (type.isInstance(element)) {
        matches.add(type.cast(element));
      }
    }
    return matches;
  }

  /**
   * {@code toTerraform()}: the stack document as raw values.
   *
   * @return the document
   */
  public Map<String, Object> toTerraform() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("backend", "local");
    metadata.put("stackName", id());
    metadata.put("version", CDKTN_VERSION);
    Map<String, Object> comment = new LinkedHashMap<>();
    comment.put("metadata", metadata);
    comment.put("outputs", new LinkedHashMap<String, Object>());
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("//", comment);
    for (TerraformElement element : elements()) {
      DeepMerge.deepMerge(document, element.toTerraform());
      if (!element.toMetadata().isEmpty()) {
        DeepMerge.deepMerge(comment, element.toMetadata());
      }
    }
    if (!comment.containsKey("outputs")) {
      comment.put("outputs", new LinkedHashMap<String, Object>());
    }
    Map<String, Object> local = new LinkedHashMap<>();
    local.put("path", backendPath());
    Map<String, Object> backend = new LinkedHashMap<>();
    backend.put("local", local);
    Map<String, Object> terraform = new LinkedHashMap<>();
    terraform.put("backend", backend);
    Map<String, Object> backendFragment = new LinkedHashMap<>();
    backendFragment.put("terraform", terraform);
    DeepMerge.deepMerge(document, backendFragment);
    return document;
  }

  /**
   * @return the {@code cdk.tf.json} text: {@code JSON.stringify(document, null, 2)} with sorted keys
   */
  public String toJson() {
    return JsonText.stringifySorted(toTerraform(), 2);
  }
}
