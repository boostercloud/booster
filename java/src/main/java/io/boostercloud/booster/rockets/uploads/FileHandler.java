package io.boostercloud.booster.rockets.uploads;

import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.core.Json;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Equivalent of the rocket's FileHandler, with explicit authorization before issuing capabilities. */
public final class FileHandler {
    private final StorageConfiguration configuration;
    private final FileStorage storage;
    private final AccessPolicy uploadPolicy;
    private final AccessPolicy readPolicy;
    private final RequestContext context;
    private final Duration validity;

    public FileHandler(List<StorageConfiguration> configurations, Map<String, FileStorage> providers,
                       String storageName, RequestContext context, AccessPolicy uploadPolicy, AccessPolicy readPolicy) {
        this(configurations, providers, storageName, context, uploadPolicy, readPolicy, Duration.ofDays(1));
    }

    public FileHandler(List<StorageConfiguration> configurations, Map<String, FileStorage> providers,
                       String storageName, RequestContext context, AccessPolicy uploadPolicy, AccessPolicy readPolicy,
                       Duration validity) {
        if (configurations.isEmpty()) throw new IllegalArgumentException("Missing userConfiguration");
        if (storageName == null && configurations.size() != 1) throw new IllegalArgumentException("Missing storageAccountName");
        configuration = storageName == null ? configurations.getFirst() : configurations.stream()
                .filter(c -> c.storageName().equals(storageName.toLowerCase(Locale.ROOT))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Storage account name not found"));
        storage = Objects.requireNonNull(providers.get(configuration.storageName()), "Storage provider not configured");
        this.context = Objects.requireNonNull(context);
        this.uploadPolicy = Objects.requireNonNull(uploadPolicy);
        this.readPolicy = Objects.requireNonNull(readPolicy);
        validateValidity(validity);
        this.validity = validity;
    }

    public URI presignedPut(String directory, String fileName) {
        authorize(uploadPolicy, "presignedPut", directory, fileName); configuration.requireDirectory(directory);
        FilePaths.key(directory, fileName);
        return storage.presignedPut(configuration, directory, fileName, validity);
    }

    public URI presignedGet(String directory, String fileName) {
        authorize(readPolicy, "presignedGet", directory, fileName); configuration.requireDirectory(directory);
        FilePaths.key(directory, fileName);
        return storage.presignedGet(configuration, directory, fileName, validity);
    }

    public List<ListItem> list(String directory) {
        authorize(readPolicy, "list", directory, null); configuration.requireDirectory(directory);
        return List.copyOf(storage.list(configuration, directory));
    }

    private void authorize(AccessPolicy policy, String operation, String directory, String fileName) {
        var input = Json.MAPPER.createObjectNode().put("operation", operation).put("storageName", configuration.storageName())
                .put("containerName", configuration.containerName()).put("directory", directory);
        if (fileName != null) input.put("fileName", fileName);
        if (!policy.permits(context, input)) throw new BoosterException(BoosterException.Code.FORBIDDEN, "File access denied");
    }

    static void validateValidity(Duration validity) {
        if (validity == null || validity.compareTo(Duration.ofSeconds(1)) < 0 || validity.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("URL validity must be between one second and seven days");
    }
}
