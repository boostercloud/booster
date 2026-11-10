package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientSecretCredentialTest {

  private final FakeHttpTransport http = new FakeHttpTransport();

  @Test
  void requests_a_client_credentials_token_and_caches_it() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-10T10:00:00Z"));
    Clock clock = new Clock() {
      @Override
      public java.time.ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(java.time.ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now.get();
      }
    };
    ClientSecretCredential credential = new ClientSecretCredential("tenant", "client", "s3cr3t&=", http, clock);
    assertThat(credential.getToken("https://management.azure.com/.default")).isEqualTo("arm-token");
    assertThat(credential.getToken("https://management.azure.com/.default")).isEqualTo("arm-token");
    assertThat(http.requests()).hasSize(1);
    HttpTransport.Request request = http.requests().get(0);
    assertThat(request.uri()).hasToString("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    assertThat(request.header("Content-Type")).contains("application/x-www-form-urlencoded");
    assertThat(request.bodyText()).isEqualTo("client_id=client&scope=https%3A%2F%2Fmanagement.azure.com%2F.default"
        + "&client_secret=s3cr3t%26%3D&grant_type=client_credentials");

    now.set(now.get().plusSeconds(3599 - 60));
    credential.getToken("https://management.azure.com/.default");
    assertThat(http.requests()).hasSize(2);
  }

  @Test
  void reports_authentication_failures() {
    http.on("POST https://login.microsoftonline.com/", HttpTransport.Response.of(401,
        "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret provided.\"}"));
    ClientSecretCredential credential = new ClientSecretCredential("t", "c", "s", http, Clock.systemUTC());
    assertThatThrownBy(() -> credential.getToken("scope"))
        .isInstanceOfSatisfying(AuthenticationError.class, error -> assertThat(error.statusCode()).isEqualTo(401))
        .hasMessage("ClientSecretCredential authentication failed: AADSTS7000215: Invalid client secret provided.");
  }

  @Test
  void reports_transport_failures() {
    http.failing("POST https://login.microsoftonline.com/");
    ClientSecretCredential credential = new ClientSecretCredential("t", "c", "s", http, Clock.systemUTC());
    assertThatThrownBy(() -> credential.getToken("scope"))
        .isInstanceOf(AuthenticationError.class)
        .hasMessageStartingWith("ClientSecretCredential authentication failed: ");
  }
}
