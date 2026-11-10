package com.boostercloud.integration.providerspecific.local;

import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-specific/local/auth.integration.ts}: the sign-up, confirmation, sign-in and
 * sign-out endpoints ({@code /auth/...}) of a local authentication API.
 *
 * <p>TypeScript disables the whole suite ({@code xdescribe}, {@code FIXME: When JWT auth is merged}) and no npm
 * script runs the file; neither the TypeScript nor the Java local provider serves an {@code /auth} route (tokens come
 * from the configured token verifiers, see {@code AuthHelper}). The class is {@link Disabled} with the TypeScript
 * reason, so no application is started for it. {@code fetch} is {@link HttpClient}: like {@code fetch}, a request
 * answers with any status, and {@code response.json()} is {@link BoosterJson#parse}. The helpers of
 * {@code integration/provider-specific/local/utils.ts} ({@code createUser}, {@code confirmUser} and the URL helpers)
 * are private methods; the URLs are resolved against the application's GraphQL URL as {@code new URL(path,
 * LOCAL_PROVIDER_HOST)} does.
 */
@Disabled("FIXME: When JWT auth is merged")
@EndToEnd
@DisplayName("With the auth API")
class LocalAuthIT {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private final ApplicationUnderTest applicationUnderTest;
  private URI localProviderHost;

  LocalAuthIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    localProviderHost = URI.create(applicationUnderTest.outputs().graphqlURL());
  }

  /** A {@code fetch} answer: the status and the parsed JSON body. */
  private record Response(int status, Object message) {}

  private Response fetch(String url, String method, Object body) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request.method(method, HttpRequest.BodyPublishers.ofString(BoosterJson.toJson(body)));
    }
    try {
      HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), BoosterJson.parse(response.body()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Object> object(Object... keysAndValues) {
    Map<String, Object> object = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      object.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return object;
  }

  private Map<String, Object> signUpBody(String username, String password, String role) {
    return object("username", username, "password", password, "userAttributes", object("role", role));
  }

  // --- Auth helpers (utils.ts) ---

  private void createUser(String username, String password) {
    Response response = fetch(signUpURL(), "POST", signUpBody(username, password, ""));
    if (response.status() != 200) {
      throw new IllegalStateException("Failed to create a new user");
    }
  }

  private void confirmUser(String username) {
    Response response = fetch(confirmUserURL(username), "GET", null);
    if (response.status() != 200) {
      throw new IllegalStateException("Failed to confirm user");
    }
  }

  // --- URL helpers (utils.ts) ---

  private String signUpURL() {
    return localProviderHost.resolve("auth/sign-up").toString();
  }

  private String confirmUserURL(String username) {
    return localProviderHost.resolve("auth/confirm/" + username).toString();
  }

  private String signInURL() {
    return localProviderHost.resolve("auth/sign-in").toString();
  }

  private String signOutURL() {
    return localProviderHost.resolve("auth/sign-out").toString();
  }

  @Nested
  @Order(1)
  @DisplayName("sign-up")
  class SignUp {

    @Nested
    @Order(1)
    @DisplayName("new user")
    class NewUser {
      private String userEmail;
      private String userPassword;

      @BeforeEach
      void beforeEach() {
        userEmail = internet.email();
        userPassword = internet.password(15);
      }

      @Test
      @Order(1)
      @DisplayName("should successfully register a new user")
      void shouldSuccessfullyRegisterANewUser() {
        Response response = fetch(signUpURL(), "POST", signUpBody(userEmail, userPassword, ""));

        assertThat((Map<?, ?>) response.message()).isEmpty();
        assertThat(response.status()).isEqualTo(200);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("repeated user")
    class RepeatedUser {
      private String userEmail;
      private String userPassword;

      @BeforeEach
      void beforeEach() {
        userEmail = internet.email();
        userPassword = internet.password(15);
      }

      @Test
      @Order(1)
      @DisplayName("should return a 401 error, username already registered")
      void shouldReturnA401ErrorUsernameAlreadyRegistered() {
        // First user registration
        Response response = fetch(signUpURL(), "POST", signUpBody(userEmail, userPassword, ""));

        assertThat((Map<?, ?>) response.message()).isEmpty();
        assertThat(response.status()).isEqualTo(200);

        // repeated user registration
        response = fetch(signUpURL(), "POST", signUpBody(userEmail, userPassword, ""));

        assertThat((Map<?, ?>) response.message()).isNotEmpty();
        assertThat(response.message()).isEqualTo(object("title", "Not Authorized Error",
            "reason", "User with username " + userEmail + " is already registered"));
        assertThat(response.status()).isEqualTo(401);
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("confirm user")
  class ConfirmUser {
    private String userEmail;
    private String userPassword;

    @BeforeEach
    void beforeEach() {
      userEmail = internet.email();
      userPassword = internet.password(15);
    }

    @Nested
    @Order(1)
    @DisplayName("registered username")
    class RegisteredUsername {
      @Test
      @Order(1)
      @DisplayName("should successfully confirm user")
      void shouldSuccessfullyConfirmUser() {
        createUser(userEmail, userPassword);

        Response response = fetch(confirmUserURL(userEmail), "GET", null);

        assertThat(response.message()).isEqualTo("User confirmed!");
        assertThat(response.status()).isEqualTo(200);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("not registered username")
    class NotRegisteredUsername {
      private String notRegisteredUserEmail;

      @BeforeEach
      void beforeEach() {
        notRegisteredUserEmail = internet.email();
      }

      @Test
      @Order(1)
      @DisplayName("should return a 404 error with expected message")
      void shouldReturnA404ErrorWithExpectedMessage() {
        Response response = fetch(confirmUserURL(notRegisteredUserEmail), "GET", null);

        assertThat(response.message()).isEqualTo(object("title", "Not Found Error",
            "reason", "Incorrect username " + notRegisteredUserEmail));
        assertThat(response.status()).isEqualTo(404);
      }
    }
  }

  @Nested
  @Order(3)
  @DisplayName("sign-in")
  class SignIn {

    @Nested
    @Order(1)
    @DisplayName("invalid username")
    class InvalidUsername {
      private String userEmail;
      private String userPassword;

      @BeforeEach
      void beforeEach() {
        userEmail = internet.email();
        userPassword = internet.password(15);
      }

      @Test
      @Order(1)
      @DisplayName("should return a 401 error with expected message")
      void shouldReturnA401ErrorWithExpectedMessage() {
        Response response = fetch(signInURL(), "POST", object("username", userEmail, "password", userPassword));

        assertThat(response.message()).isEqualTo(object("title", "Not Authorized Error",
            "reason", "Incorrect username or password"));
        assertThat(response.status()).isEqualTo(401);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("not confirmed username")
    class NotConfirmedUsername {
      private String userEmail;
      private String userPassword;

      @BeforeEach
      void beforeEach() {
        userEmail = internet.email();
        userPassword = internet.password(15);

        createUser(userEmail, userPassword);
      }

      @Test
      @Order(1)
      @DisplayName("should return a 401 error with expected message")
      void shouldReturnA401ErrorWithExpectedMessage() {
        Response response = fetch(signInURL(), "POST", object("username", userEmail, "password", userPassword));

        assertThat(response.message()).isEqualTo(object("title", "Not Authorized Error",
            "reason", "User with username " + userEmail + " has not been confirmed"));
        assertThat(response.status()).isEqualTo(401);
      }
    }

    @Nested
    @Order(3)
    @DisplayName("valid confirmed username")
    class ValidConfirmedUsername {
      private String userEmail;
      private String userPassword;

      @BeforeEach
      void beforeEach() {
        userEmail = internet.email();
        userPassword = internet.password(15);

        createUser(userEmail, userPassword);
        confirmUser(userEmail);
      }

      @Test
      @Order(1)
      @DisplayName("should sign-in successfully")
      void shouldSignInSuccessfully() {
        Response response = fetch(signInURL(), "POST", object("username", userEmail, "password", userPassword));

        assertThat(response.message()).isNotNull();
        assertThat(response.message()).isNotEqualTo(Map.of()).isNotEqualTo("");
        assertThat(response.status()).isEqualTo(200);
      }
    }
  }

  @Nested
  @Order(4)
  @DisplayName("sign-out")
  class SignOut {

    @Nested
    @Order(1)
    @DisplayName("missing token")
    class MissingToken {
      @Test
      @Order(1)
      @DisplayName("should return a 400 error with expected message")
      void shouldReturnA400ErrorWithExpectedMessage() {
        Response response = fetch(signOutURL(), "POST", Map.of());

        assertThat(response.message()).isEqualTo("accessToken field not set");
        assertThat(response.status()).isEqualTo(400);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("token provided")
    class TokenProvided {
      @Test
      @Order(1)
      @DisplayName("should successfully sign-out")
      void shouldSuccessfullySignOut() {
        Response response = fetch(signOutURL(), "POST", object("accessToken", random.uuid()));

        assertThat(response.message()).isEqualTo("");
        assertThat(response.status()).isEqualTo(200);
      }
    }
  }
}
