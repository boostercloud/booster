package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.Construct;
import java.util.HashSet;
import java.util.Set;

/**
 * Java-only: the construct ids TypeScript computes from unresolved tokens. {@code TerraformStorageContainer} and
 * {@code TerraformRoleAssignment} call {@code toTerraformName} on token strings ({@code ${TfToken[TOKEN.12]}}), and
 * every read of a token attribute allocates a new token number, so their ids are opaque but always distinct. Java
 * passes the names those tokens resolve to, which gives readable ids; {@code toTerraformName} keeps only the end of a
 * long name, so two storages whose names end alike would get the same id. When that happens the name gets a counter
 * ({@code <name>2}, {@code <name>3}...) until the id is free, so the synthesis never fails where TypeScript succeeds.
 */
final class ConstructIds {

  private ConstructIds() {}

  /**
   * @param scope the stack the construct is added to
   * @param utils the rocket utilities ({@code toTerraformName})
   * @param name the name part of the id
   * @param suffix the fixed suffix ({@code rfsb<container>}, {@code rfrlb}, {@code rfrlr})
   * @return {@code toTerraformName(name, suffix)}, or the first {@code toTerraformName(name + n, suffix)} (n = 2, 3...)
   *     no other construct of the scope uses
   * @throws IllegalArgumentException {@code Error. Suffix could not be greater than 24} for a long suffix, as in
   *     TypeScript
   */
  static String unique(Construct scope, RocketUtils utils, String name, String suffix) {
    String id = utils.toTerraformName(name, suffix);
    Set<String> taken = new HashSet<>();
    for (Construct child : scope.children()) {
      taken.add(child.id());
    }
    for (int n = 2; taken.contains(id); n++) {
      id = utils.toTerraformName(name + n, suffix);
    }
    return id;
  }
}
