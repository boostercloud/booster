package com.boostercloud.rocket.fileuploads.local;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.local.handlers.DeleteFile;
import com.boostercloud.rocket.fileuploads.local.handlers.ListHandler;
import com.boostercloud.rocket.fileuploads.local.handlers.PresignedGet;
import com.boostercloud.rocket.fileuploads.local.handlers.PresignedPut;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.RocketFilesProvider;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.List;

/**
 * The local provider runtime library: the module object of {@code @boostercloud/rocket-file-uploads-local}
 * ({@code src/index.ts} re-exports the handlers and {@code file-uploaded.ts}), loaded by {@code core} from
 * {@code RocketProviderPackageType.LOCAL}.
 */
public final class RocketFilesLibrary implements RocketFilesProvider {

  /** Creates the library (the core loader instantiates it once). */
  public RocketFilesLibrary() {}

  @Override
  public String presignedGet(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return PresignedGet.presignedGet(config, rocketFilesUserConfiguration, directory, fileName);
  }

  @Override
  public Object presignedPut(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return PresignedPut.presignedPut(config, rocketFilesUserConfiguration, directory, fileName);
  }

  @Override
  public List<ListItem> list(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory) {
    return ListHandler.list(config, rocketFilesUserConfiguration, directory);
  }

  @Override
  public boolean deleteFile(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return DeleteFile.deleteFile(config, rocketFilesUserConfiguration, directory, fileName);
  }

  @Override
  public Object getMetadataFromRequest(Object request) {
    return FileUploaded.getMetadataFromRequest(request);
  }

  @Override
  public boolean validateMetadata(RocketFilesUserConfiguration configuration, Object metadata) {
    return FileUploaded.validateMetadata(configuration, metadata);
  }
}
