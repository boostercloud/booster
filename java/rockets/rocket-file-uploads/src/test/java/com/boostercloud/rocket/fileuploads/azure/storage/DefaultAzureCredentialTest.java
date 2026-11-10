package com.boostercloud.rocket.fileuploads.azure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.azure.AuthenticationError;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The {@code DefaultAzureCredential} chain: environment, managed identity, Azure CLI. */
class DefaultAzureCredentialTest {

  private static final String SCOPE = "https://storage.azure.com/.default";
  private static final List<String> VARIABLES = List.of("AZURE_TENANT_ID", "AZURE_CLIENT_ID", "AZURE_CLIENT_SECRET",
      "IDENTITY_ENDPOINT", "IDENTITY_HEADER");
  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

  private final List<HttpTransport.Request> requests = new ArrayList<>();
  private final List<List<String>> commands = new ArrayList<>();
  private Function<HttpTransport.Request, HttpTransport.Response> responder;
  private DefaultAzureCredential.CommandRunner cli;
  private DefaultAzureCredential credential;

  @BeforeEach
  void setUp() {
    VARIABLES.forEach(name -> EnvironmentVariables.set(name, ""));
    responder = request -> {
      throw new UncheckedIOException(new IOException("connect timed out"));
    };
    cli = command -> {
      throw new IOException("az not found");
    };
    HttpTransport transport = request -> {
      requests.add(request);
      return responder.apply(request);
    };
    credential = new DefaultAzureCredential(transport, command -> {
      commands.add(command);
      return cli.run(command);
    }, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    VARIABLES.forEach(EnvironmentVariables::unset);
  }

  @Test
  void a_service_principal_from_the_environment_comes_first() {
    EnvironmentVariables.set("AZURE_TENANT_ID", "tenant");
    EnvironmentVariables.set("AZURE_CLIENT_ID", "client");
    EnvironmentVariables.set("AZURE_CLIENT_SECRET", "secret");
    responder = request -> HttpTransport.Response.of(200, "{\"access_token\":\"sp-token\",\"expires_in\":3600}");

    assertThat(credential.getToken(SCOPE)).isEqualTo("sp-token");
    assertThat(credential.getToken(SCOPE)).isEqualTo("sp-token");

    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().uri().toString())
        .isEqualTo("https://login.microsoftonline.com/tenant/oauth2/v2.0/token");
    assertThat(requests.getFirst().bodyText()).contains("client_id=client", "grant_type=client_credentials",
        "scope=https%3A%2F%2Fstorage.azure.com%2F.default");
  }

  @Test
  void the_app_service_managed_identity_is_used_in_function_apps() {
    EnvironmentVariables.set("IDENTITY_ENDPOINT", "http://127.0.0.1:41000/msi/token");
    EnvironmentVariables.set("IDENTITY_HEADER", "header-secret");
    responder = request -> HttpTransport.Response.of(200,
        "{\"access_token\":\"mi-token\",\"expires_on\":\"" + (NOW.getEpochSecond() + 3600) + "\"}");

    assertThat(credential.getToken(SCOPE)).isEqualTo("mi-token");
    assertThat(credential.getToken(SCOPE)).isEqualTo("mi-token");

    assertThat(requests).hasSize(1);
    HttpTransport.Request request = requests.getFirst();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.uri().toString()).isEqualTo(
        "http://127.0.0.1:41000/msi/token?api-version=2019-08-01&resource=https%3A%2F%2Fstorage.azure.com");
    assertThat(request.header("X-IDENTITY-HEADER")).hasValue("header-secret");
  }

  @Test
  void a_user_assigned_identity_is_selected_by_client_id() {
    EnvironmentVariables.set("AZURE_CLIENT_ID", "uami");
    responder = request -> HttpTransport.Response.of(200, "{\"access_token\":\"imds-token\",\"expires_in\":\"600\"}");

    assertThat(credential.getToken(SCOPE)).isEqualTo("imds-token");

    HttpTransport.Request request = requests.getFirst();
    assertThat(request.uri().toString()).isEqualTo("http://169.254.169.254/metadata/identity/oauth2/token"
        + "?api-version=2018-02-01&resource=https%3A%2F%2Fstorage.azure.com&client_id=uami");
    assertThat(request.header("Metadata")).hasValue("true");
  }

  @Test
  void the_azure_cli_is_the_last_resort() {
    cli = command -> new DefaultAzureCredential.CommandResult(0,
        "{\"accessToken\":\"cli-token\",\"expires_on\":" + (NOW.getEpochSecond() + 3600) + "}", "");

    assertThat(credential.getToken(SCOPE)).isEqualTo("cli-token");
    assertThat(commands.getFirst()).endsWith("account", "get-access-token", "--output", "json", "--resource",
        "https://storage.azure.com");
  }

  @Test
  void expired_tokens_are_requested_again() {
    cli = command -> new DefaultAzureCredential.CommandResult(0,
        "{\"accessToken\":\"cli-token\",\"expires_on\":" + (NOW.getEpochSecond() + 60) + "}", "");

    credential.getToken(SCOPE);
    credential.getToken(SCOPE);

    assertThat(commands).hasSize(2);
  }

  @Test
  void without_any_credential_the_chain_fails_with_every_reason() {
    assertThatThrownBy(() -> credential.getToken(SCOPE))
        .isInstanceOf(AuthenticationError.class)
        .hasMessageStartingWith("ChainedTokenCredential authentication failed.\n"
            + "CredentialUnavailableError: EnvironmentCredential is unavailable.")
        .hasMessageContaining("CredentialUnavailableError: ManagedIdentityCredential: no managed identity endpoint")
        .hasMessageContaining("CredentialUnavailableError: Azure CLI could not be found.");
  }

  @Test
  void a_cli_that_is_not_logged_in_is_unavailable_and_other_failures_stop_the_chain() {
    cli = command -> new DefaultAzureCredential.CommandResult(1, "", "ERROR: Please run 'az login' to setup account.");
    assertThatThrownBy(() -> credential.getToken(SCOPE))
        .hasMessageContaining("Please run 'az login' from a command prompt to authenticate before using this credential.");

    EnvironmentVariables.set("IDENTITY_ENDPOINT", "http://127.0.0.1:41000/msi/token");
    EnvironmentVariables.set("IDENTITY_HEADER", "header-secret");
    responder = request -> HttpTransport.Response.of(500, "{\"message\":\"boom\"}");
    assertThatThrownBy(() -> credential.getToken(SCOPE))
        .isInstanceOf(AuthenticationError.class)
        .hasMessage("ManagedIdentityCredential authentication failed: boom");
  }

  @Test
  void the_resource_of_a_scope() {
    assertThat(DefaultAzureCredential.resourceOf(SCOPE)).isEqualTo("https://storage.azure.com");
    assertThat(DefaultAzureCredential.resourceOf("https://x")).isEqualTo("https://x");
  }
}
