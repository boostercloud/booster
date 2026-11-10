package com.boostercloud.framework.provider.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosAuthorization;
import com.boostercloud.framework.provider.azure.eventhubs.rest.SharedAccessSignatures;
import com.boostercloud.framework.provider.azure.webpubsub.rest.WebPubSubTokens;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Known-answer tests of the three request signatures, re-derived from the public service documentation and computed
 * outside the JVM (OpenSSL), so they do not share any code or assumption with the implementation:
 *
 * <ul>
 *   <li><b>Cosmos DB master key</b> ("Access control in the Azure Cosmos DB SQL API"): HMAC-SHA256 keyed with the
 *       base64-<b>decoded</b> account key over {@code verb.toLowerCase() + "\n" + resourceType.toLowerCase() + "\n" +
 *       resourceLink + "\n" + date.toLowerCase() + "\n" + "" + "\n"}, header
 *       {@code encodeURIComponent("type=master&ver=1.0&sig=" + base64(signature))} (the {@code @azure/cosmos}
 *       encoding: upper-case percent escapes).
 *       <pre>
 * printf 'post\ndocs\ndbs/my-store-app/colls/my-store-app-events-store\ntue, 10 nov 2026 09:15:04 gmt\n\n' \
 *   | openssl dgst -sha256 -mac HMAC -macopt hexkey:$(printf '%s' "$KEY" | base64 -d | xxd -p | tr -d '\n') -binary \
 *   | base64</pre>
 *   <li><b>Event Hubs SAS</b> ("Generate a Shared Access Signature token"): HMAC-SHA256 keyed with the UTF-8 bytes of
 *       the key (not decoded) over {@code encodeURIComponent(resourceUri) + "\n" + expiry}, token
 *       {@code SharedAccessSignature sr=<encoded uri>&sig=<encoded base64 signature>&se=<expiry>&skn=<key name>}.
 *       <pre>
 * printf 'https%%3A%%2F%%2Fmy-ns.servicebus.windows.net%%2Fbooster_events\n1762770904' \
 *   | openssl dgst -sha256 -mac HMAC -macopt key:$SAS_KEY -binary | base64</pre>
 *   <li><b>Web PubSub access key JWT</b> (data-plane REST API "Using AccessKey", {@code webPubSubKeyCredentialPolicy}):
 *       HS256 compact JWT keyed with the UTF-8 bytes of the access key, header {@code {"alg":"HS256","typ":"JWT"}},
 *       claims {@code iat}, {@code exp = iat + 3600} and {@code aud} = the request URL (query included, port
 *       removed).
 *       <pre>
 * H=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url); P=$(printf '{"iat":...,"exp":...,"aud":"..."}' | b64url)
 * printf '%s.%s' "$H" "$P" | openssl dgst -sha256 -mac HMAC -macopt key:$ACCESS_KEY -binary | b64url</pre>
 * </ul>
 */
class SignatureKnownAnswerTest {

  /** The well-known Cosmos DB emulator key (public). */
  static final String COSMOS_KEY =
      "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
  static final String X_MS_DATE = "Tue, 10 Nov 2026 09:15:04 GMT";
  static final String SAS_KEY = "3hJ5kTq1b2pL8wXy9ZsA4cV7nM0oRf6gH2iJ8kL0mN4=";
  static final String ACCESS_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg=";

  @Test
  void cosmosMasterKeyAuthorizationOfAFeedRequest() {
    assertThat(CosmosAuthorization.formatDate(Instant.parse("2026-11-10T09:15:04Z"))).isEqualTo(X_MS_DATE);
    assertThat(CosmosAuthorization.stringToSign(
        "POST", "docs", "dbs/my-store-app/colls/my-store-app-events-store", X_MS_DATE))
        .isEqualTo("post\ndocs\ndbs/my-store-app/colls/my-store-app-events-store\ntue, 10 nov 2026 09:15:04 gmt\n\n");
    assertThat(CosmosAuthorization.authorizationHeader(
        "POST", "docs", "dbs/my-store-app/colls/my-store-app-events-store", X_MS_DATE, COSMOS_KEY))
        .isEqualTo("type%3Dmaster%26ver%3D1.0%26sig%3DXuEh2F4bCOG3rREuSUmm222bbJt9qv1BApKH%2Bz%2FrTlU%3D");
  }

  @Test
  void cosmosMasterKeyAuthorizationOfADocumentRequest() {
    assertThat(CosmosAuthorization.authorizationHeader(
        "GET", "docs", "dbs/my-store-app/colls/my-store-app-CartReadModel/docs/42", X_MS_DATE, COSMOS_KEY))
        .isEqualTo("type%3Dmaster%26ver%3D1.0%26sig%3DrSmgGiT3Z0tbJewzGsEqfFgyrD3R84wtO79qPqPGxdg%3D");
  }

  @Test
  void eventHubsSharedAccessSignature() {
    assertThat(SharedAccessSignatures.token("https://my-ns.servicebus.windows.net/booster_events",
        "RootManageSharedAccessKey", SAS_KEY, 1762770904L))
        .isEqualTo("SharedAccessSignature sr=https%3A%2F%2Fmy-ns.servicebus.windows.net%2Fbooster_events"
            + "&sig=PyEUMQyZ44m1J%2Bl87mEYRL905QEvc9OktV1wzpu8EYg%3D&se=1762770904&skn=RootManageSharedAccessKey");
  }

  @Test
  void webPubSubAccessKeyJwt() {
    String audience = WebPubSubTokens.audience(URI.create(
        "https://my-wps.webpubsub.azure.com:443/api/hubs/booster/connections/Xy12%20abCD/:send?api-version=2021-10-01"));
    assertThat(WebPubSubTokens.accessToken(audience, ACCESS_KEY, 1762766104L)).isEqualTo(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            + ".eyJpYXQiOjE3NjI3NjYxMDQsImV4cCI6MTc2Mjc2OTcwNCwiYXVkIjoiaHR0cHM6Ly9teS13cHMud2VicHVic3ViLmF6dXJlLmNv"
            + "bS9hcGkvaHVicy9ib29zdGVyL2Nvbm5lY3Rpb25zL1h5MTIlMjBhYkNELzpzZW5kP2FwaS12ZXJzaW9uPTIwMjEtMTAtMDEifQ"
            + ".TNGCX4DuRWc8cvrQB1poK0fyyF4ETiHdSKQHpYGSG94");
  }
}
