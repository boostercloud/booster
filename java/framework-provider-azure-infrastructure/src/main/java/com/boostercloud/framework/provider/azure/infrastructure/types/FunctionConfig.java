package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The content of a {@code function.json} ({@code FunctionDefinition.config}): the bindings, the file with the code and
 * the entry point. For the Java worker {@code scriptFile} is the application jar relative to the function folder
 * ({@code ../<app>.jar}) and {@code entryPoint} is {@code <fully qualified class>.<method>}.
 *
 * @param bindings the bindings, trigger first
 * @param scriptFile the code file
 * @param entryPoint the function to call
 */
public record FunctionConfig(List<Binding> bindings, String scriptFile, String entryPoint) {

  /** Canonical constructor: copies the bindings. */
  public FunctionConfig {
    bindings = List.copyOf(bindings);
    Objects.requireNonNull(scriptFile, "scriptFile");
    Objects.requireNonNull(entryPoint, "entryPoint");
  }

  /**
   * @return the JSON object {@code { bindings, scriptFile, entryPoint }} (TypeScript key order)
   */
  public Map<String, Object> toRaw() {
    List<Object> rawBindings = new ArrayList<>();
    for (Binding binding : bindings) {
      rawBindings.add(new LinkedHashMap<>(binding.properties()));
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("bindings", rawBindings);
    raw.put("scriptFile", scriptFile);
    raw.put("entryPoint", entryPoint);
    return raw;
  }
}
