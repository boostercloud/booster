package com.boostercloud.rocket.fileuploads.azure.storage;

import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.http.UriEncoding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The Blob service REST calls the rocket makes (what {@code BlobServiceClient} / {@code ContainerClient} of
 * {@code @azure/storage-blob} send), over {@link HttpTransport} with an Azure AD bearer token:
 *
 * <ul>
 *   <li>{@code POST <service>/?restype=service&comp=userdelegationkey} with
 *       {@code <KeyInfo><Start>..</Start><Expiry>..</Expiry></KeyInfo>} ({@code getUserDelegationKey});</li>
 *   <li>{@code GET <service>/<container>?restype=container&comp=list&prefix=..&marker=..&include=metadata}, following
 *       {@code NextMarker} until the last page ({@code listBlobsFlat({ prefix, includeMetadata: true })});</li>
 *   <li>{@code DELETE <service>/<container>/<blob>} (Java addition for {@code deleteFile}).</li>
 * </ul>
 *
 * Every request carries {@code x-ms-version}, {@code x-ms-date} (two-digit day, as the SDK) and
 * {@code Authorization: Bearer <token>}; failures
 * are {@link StorageError}s with the service's error code and message.
 */
public final class BlobRestClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  /** Parses the dates of the answers (RFC 1123, one- or two-digit day). */
  private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;
  /**
   * {@code x-ms-date}: {@code Tue, 03 Nov 2026 08:05:01 GMT}, what the SDK sends ({@code Date.toUTCString()}, the
   * IMF-fixdate of RFC 7231, always a two-digit day). {@link DateTimeFormatter#RFC_1123_DATE_TIME} writes
   * {@code Tue, 3 Nov 2026} on the 1st to the 9th of each month.
   */
  static final DateTimeFormatter X_MS_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

  private final BlobStorageContext context;

  /** One blob of a listing ({@code BlobItem}, the properties the rocket reads). */
  public record BlobItem(String name, Instant createdOn, Instant lastModified, Long contentLength, String contentType,
      Map<String, String> metadata) {}

  /**
   * @param context the transport, credential, endpoint, clock and API version
   */
  public BlobRestClient(BlobStorageContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  /**
   * @param accountName the storage account
   * @return its service URL with a trailing slash ({@code blobServiceClient.url})
   */
  public String serviceUrl(String accountName) {
    String endpoint = context.endpoint().apply(accountName).toString();
    return endpoint.endsWith("/") ? endpoint : endpoint + "/";
  }

  /**
   * {@code getUserDelegationKey(startsOn, expiresOn)}: both dates are sent truncated to seconds.
   *
   * @param accountName the storage account
   * @param startsOn the start of the key validity
   * @param expiresOn its end
   * @return the key
   * @throws StorageError when the service refuses (for instance without the {@code Storage Blob Data Contributor}
   *     role)
   */
  public UserDelegationKey getUserDelegationKey(String accountName, Instant startsOn, Instant expiresOn) {
    String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><KeyInfo><Start>"
        + BlobSas.truncatedIso(startsOn) + "</Start><Expiry>" + BlobSas.truncatedIso(expiresOn)
        + "</Expiry></KeyInfo>";
    Map<String, String> headers = headers();
    headers.put("Content-Type", "application/xml; charset=utf-8");
    HttpTransport.Response response = send("POST",
        URI.create(serviceUrl(accountName) + "?restype=service&comp=userdelegationkey"), headers,
        body.getBytes(StandardCharsets.UTF_8));
    if (response.status() != 200) {
      throw error(response);
    }
    Element root = parseXml(response.body()).getDocumentElement();
    return new UserDelegationKey(
        childText(root, "SignedOid"),
        childText(root, "SignedTid"),
        parseIso(childText(root, "SignedStart")),
        parseIso(childText(root, "SignedExpiry")),
        childText(root, "SignedService"),
        childText(root, "SignedVersion"),
        childText(root, "Value"));
  }

  /**
   * {@code containerClient.listBlobsFlat({ prefix, includeMetadata: true })}: every page, in service order.
   *
   * @param accountName the storage account
   * @param containerName the container
   * @param prefix the name prefix ({@code null} or empty lists everything)
   * @return the blobs
   * @throws StorageError when a page fails
   */
  public List<BlobItem> listBlobsFlat(String accountName, String containerName, String prefix) {
    List<BlobItem> items = new ArrayList<>();
    String marker = null;
    do {
      StringBuilder url = new StringBuilder(serviceUrl(accountName))
          .append(UriEncoding.encodeURIComponent(containerName))
          .append("?restype=container&comp=list");
      if (prefix != null && !prefix.isEmpty()) {
        url.append("&prefix=").append(UriEncoding.encodeURIComponent(prefix));
      }
      if (marker != null) {
        url.append("&marker=").append(UriEncoding.encodeURIComponent(marker));
      }
      url.append("&include=metadata");
      HttpTransport.Response response = send("GET", URI.create(url.toString()), headers(), null);
      if (response.status() != 200) {
        throw error(response);
      }
      Element root = parseXml(response.body()).getDocumentElement();
      Element blobs = child(root, "Blobs");
      if (blobs != null) {
        for (Element blob : children(blobs, "Blob")) {
          items.add(blobItem(blob));
        }
      }
      String next = childText(root, "NextMarker");
      marker = next == null || next.isEmpty() ? null : next;
    } while (marker != null);
    return items;
  }

  /**
   * Deletes a blob (and its snapshots).
   *
   * @param accountName the storage account
   * @param containerName the container
   * @param blobName the blob
   * @return {@code true} when deleted, {@code false} when it did not exist
   * @throws StorageError for other failures
   */
  public boolean deleteBlob(String accountName, String containerName, String blobName) {
    Map<String, String> headers = headers();
    headers.put("x-ms-delete-snapshots", "include");
    HttpTransport.Response response = send("DELETE", URI.create(blobUrl(accountName, containerName, blobName)),
        headers, null);
    if (response.status() == 202 || response.status() == 200) {
      return true;
    }
    if (response.status() == 404) {
      String code = response.header("x-ms-error-code").orElse(null);
      if (code == null || code.equals("BlobNotFound")) {
        return false;
      }
    }
    throw error(response);
  }

  /**
   * @param accountName the storage account
   * @param containerName the container
   * @param blobName the blob
   * @return the request URL of the blob (every path segment {@code encodeURIComponent}-encoded, as the SDK)
   */
  public String blobUrl(String accountName, String containerName, String blobName) {
    StringBuilder path = new StringBuilder();
    for (String segment : blobName.split("/", -1)) {
      if (path.length() > 0) {
        path.append('/');
      }
      path.append(UriEncoding.encodeURIComponent(segment));
    }
    return serviceUrl(accountName) + UriEncoding.encodeURIComponent(containerName) + "/" + path;
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("x-ms-version", context.apiVersion());
    headers.put("x-ms-date", X_MS_DATE.format(context.clock().instant()));
    headers.put("Accept", "application/xml");
    headers.put("Authorization", "Bearer " + context.credential().getToken(BlobStorageContext.STORAGE_SCOPE));
    return headers;
  }

  private HttpTransport.Response send(String method, URI uri, Map<String, String> headers, byte[] body) {
    try {
      return context.transport().send(new HttpTransport.Request(method, uri, headers, body, TIMEOUT));
    } catch (UncheckedIOException e) {
      throw new StorageError(0, null, "Request to " + uri.getHost() + " failed: " + e.getMessage());
    }
  }

  private static BlobItem blobItem(Element blob) {
    Element nameElement = child(blob, "Name");
    String name = nameElement == null ? null : nameElement.getTextContent();
    if (nameElement != null && "true".equals(nameElement.getAttribute("Encoded")) && name != null) {
      name = URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
    Element properties = child(blob, "Properties");
    Instant createdOn = null;
    Instant lastModified = null;
    Long contentLength = null;
    String contentType = null;
    if (properties != null) {
      createdOn = parseRfc1123(childText(properties, "Creation-Time"));
      lastModified = parseRfc1123(childText(properties, "Last-Modified"));
      String length = childText(properties, "Content-Length");
      contentLength = length == null || length.isEmpty() ? null : Long.valueOf(length);
      contentType = emptyToNull(childText(properties, "Content-Type"));
    }
    Map<String, String> metadata = null;
    Element metadataElement = child(blob, "Metadata");
    if (metadataElement != null) {
      metadata = new LinkedHashMap<>();
      for (Element entry : children(metadataElement, null)) {
        metadata.put(entry.getTagName(), entry.getTextContent());
      }
    }
    return new BlobItem(name, createdOn, lastModified, contentLength, contentType, metadata);
  }

  private static StorageError error(HttpTransport.Response response) {
    String code = response.header("x-ms-error-code").orElse(null);
    String message = null;
    if (response.body().length > 0) {
      try {
        Element root = parseXml(response.body()).getDocumentElement();
        if (code == null) {
          code = childText(root, "Code");
        }
        message = childText(root, "Message");
      } catch (RuntimeException notXml) {
        message = null;
      }
    }
    if (message == null || message.isEmpty()) {
      message = "Unexpected status code: " + response.status();
    }
    return new StorageError(response.status(), code, message);
  }

  static Document parseXml(byte[] bytes) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      return builder.parse(new ByteArrayInputStream(bytes));
    } catch (ParserConfigurationException | SAXException e) {
      throw new IllegalStateException("Invalid XML response from Blob Storage: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Element child(Element parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && element.getTagName().equals(name)) {
        return element;
      }
    }
    return null;
  }

  private static List<Element> children(Element parent, String name) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (nodes.item(i) instanceof Element element && (name == null || element.getTagName().equals(name))) {
        result.add(element);
      }
    }
    return result;
  }

  private static String childText(Element parent, String name) {
    Element element = child(parent, name);
    return element == null ? null : element.getTextContent();
  }

  private static Instant parseIso(String text) {
    return text == null || text.isEmpty() ? null : Instant.parse(text);
  }

  private static Instant parseRfc1123(String text) {
    return text == null || text.isEmpty() ? null : ZonedDateTime.parse(text, RFC_1123).toInstant();
  }

  private static String emptyToNull(String text) {
    return text == null || text.isEmpty() ? null : text;
  }
}
