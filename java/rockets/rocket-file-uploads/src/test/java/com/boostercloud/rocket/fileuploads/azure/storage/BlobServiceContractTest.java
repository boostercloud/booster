package com.boostercloud.rocket.fileuploads.azure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.azure.JdkHttpTransport;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.azure.RocketFilesLibrary;
import com.boostercloud.rocket.fileuploads.azure.handlers.BlobService;
import com.boostercloud.rocket.fileuploads.core.BoosterRocketFiles;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import com.boostercloud.rocket.fileuploads.support.FakeBlobServer;
import com.boostercloud.rocket.fileuploads.support.FakeBlobServer.RecordedRequest;
import com.boostercloud.rocket.fileuploads.support.FakeBlobServer.Response;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.ListItemProperties;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests of the Azure runtime library against a fake Blob service: the REST requests (method, path, query,
 * headers, bodies), the SAS URLs (signature recomputed here from the public specification) and the mapping of the
 * answers. Responses follow the documented formats of {@code Get User Delegation Key}, {@code List Blobs} and
 * {@code Delete Blob}.
 */
class BlobServiceContractTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00.123Z");
  private static final String KEY_RESPONSE = """
      <?xml version="1.0" encoding="utf-8"?><UserDelegationKey>\
      <SignedOid>11111111-2222-3333-4444-555555555555</SignedOid>\
      <SignedTid>66666666-7777-8888-9999-000000000000</SignedTid>\
      <SignedStart>2026-11-10T10:00:00Z</SignedStart><SignedExpiry>2026-11-10T10:01:26Z</SignedExpiry>\
      <SignedService>b</SignedService><SignedVersion>2025-11-05</SignedVersion>\
      <Value>Ym9vc3Rlci1yb2NrZXQtZmlsZXMtdGVzdC1rZXktMzI=</Value></UserDelegationKey>""";
  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1"));

  private FakeBlobServer server;
  private BoosterConfig config;
  private final List<String> scopes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    server = new FakeBlobServer();
    BlobStorageContext.use(new BlobStorageContext(new JdkHttpTransport(), scope -> {
      scopes.add(scope);
      return "test-token";
    }, server::endpoint, Clock.fixed(NOW, ZoneOffset.UTC), BlobStorageContext.DEFAULT_API_VERSION));
    config = new BoosterConfig("production");
    config.appName = "test";
  }

  @AfterEach
  void tearDown() {
    BlobStorageContext.use(null);
    server.close();
  }

  private static String hmac(String text, String base64Key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static String expectedSas(String permissions) throws Exception {
    String stringToSign = String.join("\n", permissions, "2026-11-10T10:00:00Z", "2026-11-10T10:01:26Z",
        "/blob/clientst/rocketfiles/client1/myfile.txt", "11111111-2222-3333-4444-555555555555",
        "66666666-7777-8888-9999-000000000000", "2026-11-10T10:00:00Z", "2026-11-10T10:01:26Z", "b", "2025-11-05", "",
        "https,http", "2018-11-09", "b", "", "", "", "", "", "");
    String signature = hmac(stringToSign, "Ym9vc3Rlci1yb2NrZXQtZmlsZXMtdGVzdC1rZXktMzI=");
    return "sv=2018-11-09&spr=https%2Chttp&st=2026-11-10T10%3A00%3A00Z&se=2026-11-10T10%3A01%3A26Z"
        + "&skoid=11111111-2222-3333-4444-555555555555&sktid=66666666-7777-8888-9999-000000000000"
        + "&skt=2026-11-10T10%3A00%3A00Z&ske=2026-11-10T10%3A01%3A26Z&sks=b&skv=2025-11-05&sr=b&sp=" + permissions
        + "&sig=" + signature.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D");
  }

  @Test
  void presigned_put_asks_for_a_user_delegation_key_and_signs_a_write_sas() throws Exception {
    server.handler(request -> Response.xml(200, KEY_RESPONSE));

    Object url = new RocketFilesLibrary().presignedPut(config, CLIENT, "client1", "myfile.txt");

    assertThat(url).isEqualTo(server.baseUrl() + "/clientst/rocketfiles/client1/myfile.txt?" + expectedSas("w"));
    assertThat((String) url).endsWith("&sig=mmblP%2Bo2ZUvTYimtN1QpvRIvMVMdQMUhmu%2F0jKzj8yg%3D");
    RecordedRequest request = server.requests().getFirst();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.rawPath()).isEqualTo("/clientst/");
    assertThat(request.rawQuery()).isEqualTo("restype=service&comp=userdelegationkey");
    assertThat(request.header("authorization")).isEqualTo("Bearer test-token");
    assertThat(request.header("x-ms-version")).isEqualTo("2025-11-05");
    assertThat(request.header("x-ms-date")).isEqualTo("Tue, 10 Nov 2026 10:00:00 GMT");
    assertThat(request.header("content-type")).isEqualTo("application/xml; charset=utf-8");
    // the key covers [now, now + 86 400 ms]: TypeScript adds the "seconds" to a millisecond timestamp
    assertThat(request.bodyText()).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<KeyInfo><Start>2026-11-10T10:00:00Z</Start><Expiry>2026-11-10T10:01:26Z</Expiry></KeyInfo>");
    assertThat(scopes).containsExactly("https://storage.azure.com/.default");
  }

  @Test
  void presigned_get_signs_a_read_sas() throws Exception {
    server.handler(request -> Response.xml(200, KEY_RESPONSE));

    assertThat(new RocketFilesLibrary().presignedGet(config, CLIENT, "client1", "myfile.txt"))
        .isEqualTo(server.baseUrl() + "/clientst/rocketfiles/client1/myfile.txt?" + expectedSas("r"));
  }

  @Test
  void the_file_handler_uses_the_azure_library_of_the_descriptor() throws Exception {
    server.handler(request -> Response.xml(200, KEY_RESPONSE));
    config.rockets = List.of(new BoosterRocketFiles(config, new RocketFilesUserConfiguration("ClientST",
        "rocketfiles", List.of("client1"))).rocketForAzure());

    String url = new FileHandler(config).presignedPut("client1", "myfile.txt");

    assertThat(url).isEqualTo(server.baseUrl() + "/clientst/rocketfiles/client1/myfile.txt?" + expectedSas("w"));
  }

  @Test
  void the_public_endpoint_is_the_account_blob_host() {
    assertThat(BlobStorageContext.publicEndpoint("ClientST")).isEqualTo(URI.create("https://clientst.blob.core.windows.net"));
    assertThat(BlobStorageContext.defaults().endpoint().apply("acct"))
        .isEqualTo(URI.create("https://acct.blob.core.windows.net"));
    assertThat(new BlobService(config, new RocketFilesUserConfiguration("", "c", List.of())).storageAccount())
        .isEqualTo("testrfproduction");
  }

  @Test
  void a_refused_key_request_is_a_storage_error() {
    server.handler(request -> Response.xml(403, "<?xml version=\"1.0\" encoding=\"utf-8\"?><Error>"
        + "<Code>AuthorizationPermissionMismatch</Code><Message>This request is not authorized to perform this "
        + "operation using this permission.</Message></Error>")
        .withHeader("x-ms-error-code", "AuthorizationPermissionMismatch"));

    assertThatThrownBy(() -> new RocketFilesLibrary().presignedPut(config, CLIENT, "client1", "f"))
        .isInstanceOfSatisfying(StorageError.class, error -> {
          assertThat(error.statusCode()).isEqualTo(403);
          assertThat(error.errorCode()).isEqualTo("AuthorizationPermissionMismatch");
        })
        .hasMessage("This request is not authorized to perform this operation using this permission.");
  }

  @Test
  void list_follows_the_markers_and_maps_the_blobs() {
    server.handler(request -> {
      if (request.rawQuery().contains("marker=")) {
        return Response.xml(200, """
            <?xml version="1.0" encoding="utf-8"?><EnumerationResults ContainerName="rocketfiles"><Prefix>client1</Prefix>\
            <Blobs><Blob><Name Encoded="true">client1%2Fcaf%C3%A9+1.txt</Name><Properties>\
            <Last-Modified>Wed, 26 Oct 2022 05:41:00 GMT</Last-Modified><Content-Length>0</Content-Length>\
            </Properties></Blob></Blobs><NextMarker /></EnumerationResults>""");
      }
      return Response.xml(200, """
          <?xml version="1.0" encoding="utf-8"?><EnumerationResults ContainerName="rocketfiles"><Prefix>client1</Prefix>\
          <Blobs><Blob><Name>client1/myfile.txt</Name><Properties>\
          <Creation-Time>Wed, 26 Oct 2022 05:40:47 GMT</Creation-Time>\
          <Last-Modified>Wed, 26 Oct 2022 05:40:47 GMT</Last-Modified><Etag>0x8DAB71</Etag>\
          <Content-Length>6</Content-Length><Content-Type>text/plain</Content-Type><BlobType>BlockBlob</BlobType>\
          </Properties><Metadata><owner>ada</owner></Metadata></Blob>\
          <Blob><Name>client10/other.txt</Name><Properties><Last-Modified>Wed, 26 Oct 2022 05:40:48 GMT</Last-Modified>\
          </Properties><Metadata /></Blob></Blobs><NextMarker>2!72!MDAwMDA=</NextMarker></EnumerationResults>""");
    });

    List<ListItem> items = new RocketFilesLibrary().list(config, CLIENT, "client1");

    assertThat(items).containsExactly(
        new ListItem("client1/myfile.txt", new ListItemProperties(Instant.parse("2022-10-26T05:40:47Z"),
            Instant.parse("2022-10-26T05:40:47Z"), 6L, "text/plain"), Map.of("owner", "ada")),
        new ListItem("client10/other.txt", new ListItemProperties(null, Instant.parse("2022-10-26T05:40:48Z"), null,
            null), Map.of()),
        new ListItem("client1/café+1.txt", new ListItemProperties(null, Instant.parse("2022-10-26T05:41:00Z"), 0L,
            null), null));
    List<RecordedRequest> requests = server.requests();
    assertThat(requests).extracting(RecordedRequest::method).containsExactly("GET", "GET");
    assertThat(requests).extracting(RecordedRequest::rawPath).containsOnly("/clientst/rocketfiles");
    assertThat(requests).extracting(RecordedRequest::rawQuery).containsExactly(
        "restype=container&comp=list&prefix=client1&include=metadata",
        "restype=container&comp=list&prefix=client1&marker=2!72!MDAwMDA%3D&include=metadata");
    assertThat(requests.getFirst().header("authorization")).isEqualTo("Bearer test-token");
  }

  @Test
  void an_empty_directory_lists_the_whole_container() {
    server.handler(request -> Response.xml(200, "<EnumerationResults><Blobs /><NextMarker /></EnumerationResults>"));

    assertThat(new RocketFilesLibrary().list(config, CLIENT, "")).isEmpty();
    assertThat(server.requests().getFirst().rawQuery()).isEqualTo("restype=container&comp=list&include=metadata");
  }

  @Test
  void delete_removes_the_blob_and_its_snapshots() {
    server.handler(request -> request.rawPath().endsWith("gone.txt")
        ? Response.xml(404, "<Error><Code>BlobNotFound</Code><Message>The specified blob does not exist.</Message>"
            + "</Error>").withHeader("x-ms-error-code", "BlobNotFound")
        : Response.empty(202));

    assertThat(new RocketFilesLibrary().deleteFile(config, CLIENT, "client1", "my file.txt")).isTrue();
    assertThat(new RocketFilesLibrary().deleteFile(config, CLIENT, "client1", "gone.txt")).isFalse();

    RecordedRequest request = server.requests().getFirst();
    assertThat(request.method()).isEqualTo("DELETE");
    assertThat(request.rawPath()).isEqualTo("/clientst/rocketfiles/client1/my%20file.txt");
    assertThat(request.header("x-ms-delete-snapshots")).isEqualTo("include");
  }

  @Test
  void delete_refuses_file_names_that_leave_the_directory() {
    server.handler(request -> Response.empty(202));

    for (String fileName : List.of("../admin/secret.txt", "a/../../admin/secret.txt", "sub/x.txt", "..\\x", "..",
        ".", "")) {
      assertThatThrownBy(() -> new RocketFilesLibrary().deleteFile(config, CLIENT, "client1", fileName))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid file name " + fileName);
    }
    assertThat(server.requests()).isEmpty();
  }

  @Test
  void deleting_from_a_missing_container_fails() {
    server.handler(request -> Response.xml(404, "<Error><Code>ContainerNotFound</Code>"
        + "<Message>The specified container does not exist.</Message></Error>")
        .withHeader("x-ms-error-code", "ContainerNotFound"));

    assertThatThrownBy(() -> new RocketFilesLibrary().deleteFile(config, CLIENT, "client1", "a.txt"))
        .isInstanceOf(StorageError.class).hasMessage("The specified container does not exist.");
  }

  @Test
  void transport_failures_are_storage_errors() {
    server.close();

    assertThatThrownBy(() -> new RocketFilesLibrary().list(config, CLIENT, "client1"))
        .isInstanceOfSatisfying(StorageError.class, error -> assertThat(error.statusCode()).isZero())
        .hasMessageStartingWith("Request to 127.0.0.1 failed");
  }
}
