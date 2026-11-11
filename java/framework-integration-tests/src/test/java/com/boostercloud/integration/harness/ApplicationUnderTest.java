package com.boostercloud.integration.harness;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.GraphQLHelper;
import com.boostercloud.application.tester.HttpHelper;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.application.tester.TokenHelper;
import com.boostercloud.integration.helper.AppHelper;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code applicationUnderTest} of the TypeScript suites: an {@link ApplicationTester} for the running application,
 * with the same accessors ({@code applicationUnderTest.graphql().client()}, {@code token().forUser(...)},
 * {@code count().events()}, {@code query().events(key)}, {@code http().getHealthUrl()}), plus the {@link AppProcess}
 * when the harness launched it (local provider).
 *
 * <p>Its URLs do not change when the process restarts (the ports are kept), so one instance serves the whole life of
 * an {@link AppProcess}.
 */
public final class ApplicationUnderTest {

  private final ApplicationTester tester;
  private final ProviderTestHelper providerTestHelper;
  private final AppProcess process;

  private ApplicationUnderTest(ProviderTestHelper providerTestHelper, AppProcess process) {
    this.providerTestHelper = Objects.requireNonNull(providerTestHelper, "providerTestHelper");
    this.tester = new ApplicationTester(providerTestHelper);
    this.process = process;
  }

  /**
   * @param app a running application launched by the harness
   * @return the application under test, with a {@link LocalProviderTestHelper} over its project directory and ports
   */
  public static ApplicationUnderTest of(AppProcess app) {
    return new ApplicationUnderTest(LocalProviderTestHelper.of(AppHelper.applicationName(), app), app);
  }

  /**
   * @param providerTestHelper the helper of an application the harness did not launch (e.g. deployed to Azure)
   * @return the application under test
   */
  public static ApplicationUnderTest of(ProviderTestHelper providerTestHelper) {
    return new ApplicationUnderTest(providerTestHelper, null);
  }

  /**
   * @param providerTestHelper the helper of the application
   * @param app the process running it, or {@code null}
   * @return the application under test
   */
  public static ApplicationUnderTest of(ProviderTestHelper providerTestHelper, AppProcess app) {
    return new ApplicationUnderTest(providerTestHelper, app);
  }

  /**
   * @return GraphQL clients over HTTP and WebSocket
   */
  public GraphQLHelper graphql() {
    return tester.graphql();
  }

  /**
   * @return plain HTTP endpoints (the health URL)
   */
  public HttpHelper http() {
    return tester.http();
  }

  /**
   * @return JWTs accepted by the application
   */
  public TokenHelper token() {
    return tester.token();
  }

  /**
   * @return the provider's counters
   */
  public Counters count() {
    return tester.count();
  }

  /**
   * @return the provider's raw queries
   */
  public Queries query() {
    return tester.query();
  }

  /**
   * @return the application URLs
   */
  public ApplicationOutputs outputs() {
    return providerTestHelper.outputs();
  }

  /**
   * @return the wrapped application tester
   */
  public ApplicationTester tester() {
    return tester;
  }

  /**
   * @return the provider test helper
   */
  public ProviderTestHelper providerTestHelper() {
    return providerTestHelper;
  }

  /**
   * @return the process, when the harness launched the application
   */
  public Optional<AppProcess> process() {
    return Optional.ofNullable(process);
  }

  @Override
  public String toString() {
    return "ApplicationUnderTest{" + outputs() + (process == null ? "" : ", " + process) + "}";
  }
}
