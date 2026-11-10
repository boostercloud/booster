package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Pins every environment variable the infrastructure reads (through {@link EnvironmentVariables} overrides) so the
 * synthesized output does not depend on the machine running the tests, and removes the overrides afterwards.
 */
public final class TestEnvironment implements BeforeEachCallback, AfterEachCallback {

  /** The values every test starts with. */
  public static final Map<String, String> DEFAULTS = defaults();

  private static Map<String, String> defaults() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("REGION", "East US");
    values.put("BOOSTER_ENV", "production");
    values.put("BOOSTER_APP_NAME", "");
    values.put("AZURE_MAX_CONTAINER_THROUGHPUT", "1000");
    values.put("AZURE_MAX_DATABASE_THROUGHPUT", "1000");
    values.put("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "false");
    values.put("BOOSTER_USE_WAF", "false");
    values.put("BOOSTER_AZURE_JAVA_VERSION", "21");
    values.put("BOOSTER_TERRAFORM_BINARY", "terraform");
    values.put("BOOSTER_APP_CLASS", "");
    values.put("AZURE_APP_ID", "app-id");
    values.put("AZURE_SECRET", "app-secret");
    values.put("AZURE_TENANT_ID", "tenant-id");
    values.put("AZURE_SUBSCRIPTION_ID", "subscription-id");
    return Map.copyOf(values);
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    EnvironmentVariables.clearOverrides();
    DEFAULTS.forEach(EnvironmentVariables::set);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    EnvironmentVariables.clearOverrides();
  }
}
