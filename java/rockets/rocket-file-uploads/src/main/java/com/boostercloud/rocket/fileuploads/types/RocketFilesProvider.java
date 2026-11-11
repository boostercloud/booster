package com.boostercloud.rocket.fileuploads.types;

/**
 * A complete provider runtime library: TypeScript loads one module with {@code require(rocketProviderPackage)} and
 * uses it as both {@link RocketFilesProviderLibrary} ({@code FileHandler}) and {@link RocketFileUploadedLibrary} (the
 * rocket function). Java addition that names that combination; implemented by
 * {@code com.boostercloud.rocket.fileuploads.local.RocketFilesLibrary} and
 * {@code com.boostercloud.rocket.fileuploads.azure.RocketFilesLibrary}.
 */
public interface RocketFilesProvider extends RocketFilesProviderLibrary, RocketFileUploadedLibrary {}
