package io.boostercloud.booster.rockets.uploads;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One configured file store, matching RocketFilesUserConfiguration. */
public record StorageConfiguration(String storageName, String containerName, List<String> directories) {
    public StorageConfiguration {
        storageName = Objects.requireNonNull(storageName, "storageName").toLowerCase(Locale.ROOT);
        if (!storageName.matches("[a-z0-9][a-z0-9_-]{0,62}")) throw new IllegalArgumentException("Invalid storage name");
        if (containerName == null || !containerName.matches("[a-z0-9][a-z0-9-]{0,62}"))
            throw new IllegalArgumentException("Invalid container name");
        directories = List.copyOf(Objects.requireNonNull(directories, "directories"));
        if (directories.isEmpty()) throw new IllegalArgumentException("At least one allowed directory is required");
        directories.forEach(DirectoryPatterns::validatePattern);
    }

    public boolean allows(String directory) {
        return DirectoryPatterns.matches(directory, directories);
    }

    public void requireDirectory(String directory) {
        FilePaths.directory(directory);
        if (!allows(directory)) throw new IllegalArgumentException("Directory is not configured: " + directory);
    }
}
