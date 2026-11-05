package io.boostercloud.booster.rockets.uploads;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;
import io.boostercloud.booster.azure.AzureClients;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Real Blob Storage adapter using identity-backed user-delegation SAS, never account keys. */
public final class AzureBlobStorage implements FileStorage {
    private final BlobServiceClient client;
    private final Clock clock;

    public AzureBlobStorage(String storageAccountName) {
        this(defaultClient(storageAccountName), Clock.systemUTC());
    }

    public AzureBlobStorage(BlobServiceClient client, Clock clock) {
        this.client = Objects.requireNonNull(client); this.clock = Objects.requireNonNull(clock);
    }

    private static BlobServiceClient defaultClient(String account) {
        if (account == null || !account.matches("[a-z0-9]{3,24}")) throw new IllegalArgumentException("Invalid Azure storage account");
        return new BlobServiceClientBuilder().endpoint("https://" + account + ".blob.core.windows.net")
                .credential(AzureClients.credential()).httpClient(AzureClients.httpClient()).buildClient();
    }

    @Override public URI presignedPut(StorageConfiguration c, String directory, String name, Duration validity) {
        return sas(c, directory, name, validity, new BlobSasPermission().setWritePermission(true));
    }
    @Override public URI presignedGet(StorageConfiguration c, String directory, String name, Duration validity) {
        return sas(c, directory, name, validity, new BlobSasPermission().setReadPermission(true));
    }

    private URI sas(StorageConfiguration c, String directory, String name, Duration validity, BlobSasPermission permission) {
        check(c, directory); FileHandler.validateValidity(validity);
        String key = FilePaths.key(directory, name);
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        // Seven days is the service limit for a delegation key; avoid backdating past that window.
        OffsetDateTime expires = now.plus(validity);
        UserDelegationKey delegation = client.getUserDelegationKey(now, expires);
        var values = new BlobServiceSasSignatureValues(expires, permission).setStartTime(now).setProtocol(SasProtocol.HTTPS_ONLY);
        var blob = client.getBlobContainerClient(c.containerName()).getBlobClient(key);
        return URI.create(blob.getBlobUrl() + "?" + blob.generateUserDelegationSas(values, delegation));
    }

    @Override public List<ListItem> list(StorageConfiguration c, String directory) {
        check(c, directory);
        String prefix = FilePaths.directory(directory);
        if (!prefix.isEmpty()) prefix += "/";
        var options = new ListBlobsOptions().setPrefix(prefix).setDetails(new BlobListDetails().setRetrieveMetadata(true));
        var result = new ArrayList<ListItem>();
        for (var blob : client.getBlobContainerClient(c.containerName()).listBlobs(options, Duration.ofMinutes(1))) {
            var properties = blob.getProperties();
            result.add(new ListItem(blob.getName(), new ListItem.Properties(
                    properties.getCreationTime() == null ? null : properties.getCreationTime().toInstant(),
                    properties.getLastModified() == null ? null : properties.getLastModified().toInstant(),
                    properties.getContentLength(), properties.getContentType()), blob.getMetadata()));
        }
        result.sort(Comparator.comparing(ListItem::name));
        return List.copyOf(result);
    }

    private void check(StorageConfiguration c, String directory) {
        if (!c.storageName().equalsIgnoreCase(client.getAccountName())) throw new IllegalArgumentException("Storage account mismatch");
        c.requireDirectory(directory);
    }

    @Override public boolean acceptsBlobEndpoint(URI endpoint) {
        URI configured = URI.create(client.getAccountUrl());
        return Objects.equals(configured.getScheme(), endpoint.getScheme())
                && configured.getHost() != null && configured.getHost().equalsIgnoreCase(endpoint.getHost())
                && configured.getPort() == endpoint.getPort();
    }
}
