package com.boostercloud.framework.provider.azure.infrastructure;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketZipResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import java.nio.file.Path;
import java.util.List;

/**
 * The result of {@link ApplicationBuilder#buildApplication()} ({@code ApplicationBuild} of
 * {@code application-builder.ts}).
 *
 * @param azureStack the synthesized stack
 * @param stackDirectory where its {@code cdk.tf.json} was written (Terraform runs there)
 * @param zipResource the main function app package
 * @param consumerZipResource the consumer package (event streaming only), or {@code null}
 * @param rocketsZipResources the rocket packages, or {@code null} without rockets
 */
public record ApplicationBuild(
    AzureStack azureStack,
    Path stackDirectory,
    ZipResource zipResource,
    ZipResource consumerZipResource,
    List<RocketZipResource> rocketsZipResources) {}
