package com.boostercloud.rocket.fileuploads.azure.infrastructure;

import com.boostercloud.framework.common.RocketBuilder;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;

/**
 * The Azure infrastructure rocket package (TypeScript {@code @boostercloud/rocket-file-uploads-azure-infrastructure},
 * whose default export is the builder): the Azure infrastructure loads {@code <descriptor packageName>.Rocket} with
 * {@code RocketLoader} and builds it with the descriptor parameters.
 */
public final class Rocket implements RocketBuilder {

  /** Creates the builder ({@code RocketLoader} needs a public no-argument constructor). */
  public Rocket() {}

  /**
   * @param parameters the {@link RocketFilesConfiguration} of the descriptor (the record or its raw JSON form)
   * @return the infrastructure rocket
   */
  @Override
  public Object build(Object parameters) {
    return new AzureRocketFiles(RocketFilesConfiguration.from(parameters));
  }
}
