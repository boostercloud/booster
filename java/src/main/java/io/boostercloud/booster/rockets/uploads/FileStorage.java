package io.boostercloud.booster.rockets.uploads;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Provider contract used by FileHandler; identity and directory checks precede calls. */
public interface FileStorage {
    URI presignedPut(StorageConfiguration configuration, String directory, String fileName, Duration validity);
    URI presignedGet(StorageConfiguration configuration, String directory, String fileName, Duration validity);
    List<ListItem> list(StorageConfiguration configuration, String directory);
    /** Cloud notifications must map to the configured storage endpoint, not just an account-looking URL. */
    default boolean acceptsBlobEndpoint(URI endpoint) { return false; }
}
