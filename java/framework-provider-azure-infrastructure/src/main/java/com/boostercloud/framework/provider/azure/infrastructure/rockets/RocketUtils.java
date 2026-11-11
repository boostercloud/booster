package com.boostercloud.framework.provider.azure.infrastructure.rockets;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;

/**
 * The utilities handed to rocket hooks ({@code RocketUtils} of {@code rockets/rocket-utils.ts}).
 */
public interface RocketUtils {

  /**
   * {@code toTerraformName(name, suffix)} (see {@link Utils#toTerraformName(String, String)}).
   *
   * @param name the name
   * @param suffix the suffix
   * @return the Terraform-safe name
   */
  String toTerraformName(String name, String suffix);

  /**
   * {@code buildRocketUtils()}: a fresh instance (one per hook call, as in TypeScript).
   *
   * @return the utilities
   */
  static RocketUtils buildRocketUtils() {
    return new RocketUtils() {
      @Override
      public String toTerraformName(String name, String suffix) {
        return Utils.toTerraformName(name, suffix);
      }
    };
  }
}
