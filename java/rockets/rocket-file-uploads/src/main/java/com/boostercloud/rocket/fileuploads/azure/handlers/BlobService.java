package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.storage.BlobRestClient;
import com.boostercloud.rocket.fileuploads.azure.storage.BlobSas;
import com.boostercloud.rocket.fileuploads.azure.storage.BlobStorageContext;
import com.boostercloud.rocket.fileuploads.azure.storage.UserDelegationKey;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.ListItemProperties;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code handlers/blob-service.ts}: presigned URLs (user delegation SAS), listing and (Java addition)
 * deletion on the storage account of a user configuration, through the Blob service REST API
 * ({@link BlobRestClient}) authenticated with Azure AD ({@link BlobStorageContext#credential()}, the
 * {@code DefaultAzureCredential}). Writing through a SAS URL requires the {@code Storage Blob Data Contributor} role on
 * the account (the Azure infrastructure rocket grants it to the application's function app).
 */
public final class BlobService {

  /** {@code DEFAULT_PERMISSIONS}. */
  public static final String DEFAULT_PERMISSIONS = "r";
  /**
   * {@code DEFAULT_EXPIRES_ON_SECONDS}. TypeScript adds it to a millisecond timestamp, so the SAS (and its user
   * delegation key) are valid for 86.4 seconds, not one day; the port keeps that behaviour.
   */
  public static final long DEFAULT_EXPIRES_ON_SECONDS = 86400;

  private final String storageAccount;
  private final BlobStorageContext context;
  private final BlobRestClient client;

  /**
   * @param configuration the application configuration
   * @param rocketFilesUserConfiguration the storage
   */
  public BlobService(BoosterConfig configuration, RocketFilesUserConfiguration rocketFilesUserConfiguration) {
    this(configuration, rocketFilesUserConfiguration, BlobStorageContext.current());
  }

  /**
   * @param configuration the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param context how to reach Blob Storage
   */
  public BlobService(BoosterConfig configuration, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      BlobStorageContext context) {
    this.storageAccount = Constants.azureStorageName(configuration.appName, configuration.environmentName,
        rocketFilesUserConfiguration.storageName());
    this.context = Objects.requireNonNull(context, "context");
    this.client = new BlobRestClient(context);
  }

  /**
   * @return the storage account name
   */
  public String storageAccount() {
    return storageAccount;
  }

  /**
   * {@code getBlobSasUrl(containerName, directory, fileName)} with read permission.
   *
   * @param containerName the container
   * @param directory the directory
   * @param fileName the file name
   * @return {@code <service>/<container>/<directory>/<fileName>?<sas>}
   */
  public String getBlobSasUrl(String containerName, String directory, String fileName) {
    return getBlobSasUrl(containerName, directory, fileName, DEFAULT_PERMISSIONS, DEFAULT_EXPIRES_ON_SECONDS);
  }

  /**
   * {@code getBlobSasUrl(containerName, directory, fileName, permissions, expiresOnSeconds)}: the blob URL (path not
   * encoded, as in TypeScript) with a user delegation SAS.
   *
   * @param containerName the container
   * @param directory the directory
   * @param fileName the file name
   * @param permissions the SAS permissions ({@code r}, {@code w}...)
   * @param expiresOnSeconds the lifetime, added to the start in <b>milliseconds</b> (TypeScript behaviour)
   * @return {@code <service>/<container>/<directory>/<fileName>?<sas>}
   */
  public String getBlobSasUrl(String containerName, String directory, String fileName, String permissions,
      long expiresOnSeconds) {
    String filePath = directory + "/" + fileName;
    String blobSasQueryParameters = getBlobSasQueryParameters(containerName, filePath, permissions, expiresOnSeconds);
    return client.serviceUrl(storageAccount) + containerName + "/" + filePath + "?" + blobSasQueryParameters;
  }

  /**
   * {@code listBlobFolder(containerName, directory)}: every blob whose name starts with {@code directory} (a prefix,
   * so {@code client1} also lists {@code client10/...}; sub-directories included), with its creation and modification
   * times, size, content type and user metadata.
   *
   * @param containerName the container
   * @param directory the name prefix
   * @return the blobs
   */
  public List<ListItem> listBlobFolder(String containerName, String directory) {
    List<ListItem> result = new ArrayList<>();
    for (BlobRestClient.BlobItem blob : client.listBlobsFlat(storageAccount, containerName, directory)) {
      result.add(new ListItem(blob.name(), new ListItemProperties(blob.createdOn(), blob.lastModified(),
          blob.contentLength(), blob.contentType()), blob.metadata()));
    }
    return result;
  }

  /**
   * Java addition: deletes {@code <directory>/<fileName>} (and its snapshots).
   *
   * @param containerName the container
   * @param directory the directory
   * @param fileName the file name
   * @return {@code true} when deleted, {@code false} when it did not exist
   */
  public boolean deleteBlob(String containerName, String directory, String fileName) {
    return client.deleteBlob(storageAccount, containerName, directory + "/" + fileName);
  }

  /**
   * {@code getBlobSASQueryParameters(containerName, blobName, permissions, expiresOnSeconds)}: a user delegation key
   * for {@code [now, now + expiresOnSeconds ms]} and a SAS (version 2018-11-09, HTTPS and HTTP) signed with it.
   */
  private String getBlobSasQueryParameters(String containerName, String blobName, String permissions,
      long expiresOnSeconds) {
    Instant startsOn = context.clock().instant();
    Instant expiresOn = Instant.ofEpochMilli(context.clock().millis() + expiresOnSeconds);
    UserDelegationKey userDelegationKey = client.getUserDelegationKey(storageAccount, startsOn, expiresOn);
    return BlobSas.generateBlobSasQueryParameters(containerName, blobName, permissions, startsOn, expiresOn,
        userDelegationKey, storageAccount);
  }
}
