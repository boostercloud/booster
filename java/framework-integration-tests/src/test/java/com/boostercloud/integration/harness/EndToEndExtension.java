package com.boostercloud.integration.harness;

import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.integration.helper.AppHelper;
import java.util.Optional;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * The extension behind {@link EndToEnd}: starts the application of the suite and resolves the parameters of type
 * {@link ApplicationUnderTest}, {@link AppProcess} and {@link ApplicationTester}.
 *
 * <p>The shared application is registered in the root store of the run, so JUnit stops it when the run ends; an
 * {@link EndToEnd.Application#OWN} application is registered in the store of its class and stopped after it.
 */
public final class EndToEndExtension implements BeforeAllCallback, ParameterResolver {

  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(EndToEndExtension.class);

  @Override
  public void beforeAll(ExtensionContext context) {
    applicationFor(context);
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    Class<?> type = parameterContext.getParameter().getType();
    return type == ApplicationUnderTest.class || type == AppProcess.class || type == ApplicationTester.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    ApplicationUnderTest application = applicationFor(extensionContext);
    Class<?> type = parameterContext.getParameter().getType();
    if (type == ApplicationTester.class) {
      return application.tester();
    }
    if (type == AppProcess.class) {
      return application.process().orElseThrow(() -> new ParameterResolutionException(
          "No AppProcess with TESTED_PROVIDER=" + AppHelper.testedProvider() + ": the application was not launched "
              + "by the harness"));
    }
    return application;
  }

  private static ApplicationUnderTest applicationFor(ExtensionContext context) {
    ExtensionContext annotated = annotatedContext(context)
        .orElseThrow(() -> new IllegalStateException("No @EndToEnd class around " + context.getDisplayName()));
    EndToEnd endToEnd = AnnotationSupport.findAnnotation(annotated.getRequiredTestClass(), EndToEnd.class)
        .orElseThrow();
    if (endToEnd.value() == EndToEnd.Application.OWN) {
      return annotated.getStore(NAMESPACE)
          .computeIfAbsent(OwnApplication.class, key -> OwnApplication.start(annotated.getRequiredTestClass()),
              OwnApplication.class)
          .applicationUnderTest();
    }
    return context.getRoot().getStore(NAMESPACE)
        .computeIfAbsent(SharedApplication.class, key -> SharedApplication.get(), SharedApplication.class)
        .application();
  }

  /** The outermost class context whose class carries {@link EndToEnd} (nested classes share its application). */
  private static Optional<ExtensionContext> annotatedContext(ExtensionContext context) {
    ExtensionContext found = null;
    for (Optional<ExtensionContext> current = Optional.of(context); current.isPresent();
        current = current.get().getParent()) {
      ExtensionContext candidate = current.get();
      if (candidate.getTestClass().isPresent() && candidate.getTestMethod().isEmpty()
          && AnnotationSupport.isAnnotated(candidate.getRequiredTestClass(), EndToEnd.class)) {
        found = candidate;
      }
    }
    return Optional.ofNullable(found);
  }

  /** The application of one {@link EndToEnd.Application#OWN} class, closed by JUnit after the class. */
  record OwnApplication(AppProcess process, ApplicationUnderTest applicationUnderTest) implements AutoCloseable {

    static OwnApplication start(Class<?> testClass) {
      AppHelper.setEnv();
      if (!AppHelper.LOCAL.equals(AppHelper.testedProvider())) {
        throw new IllegalStateException("@EndToEnd(OWN) launches a local application; TESTED_PROVIDER is "
            + AppHelper.testedProvider());
      }
      AppProcess app = AppProcess.builder(testClass.getSimpleName()).build().start();
      try {
        return new OwnApplication(app,
            ApplicationUnderTest.of(AppHelper.getProviderTestHelper(LocalProviderTestHelper.options(app)), app));
      } catch (RuntimeException e) {
        app.close();
        throw e;
      }
    }

    @Override
    public void close() {
      try {
        if (process.isAlive()) {
          process.stop();
        }
      } finally {
        process.close();
      }
    }
  }
}
