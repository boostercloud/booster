package com.boostercloud.integration.harness;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks an end-to-end suite ({@code *IT} class): its tests (and those of its {@code @Nested} classes) receive the
 * running application through parameters of type {@link ApplicationUnderTest}, {@link AppProcess} (local provider
 * only) or {@link com.boostercloud.application.tester.ApplicationTester}, in constructors, lifecycle and test methods.
 *
 * <pre>{@code
 * @EndToEnd
 * @DisplayName("Cart end-to-end tests")
 * class CartIT {
 *   private final ApplicationUnderTest applicationUnderTest;
 *
 *   CartIT(ApplicationUnderTest applicationUnderTest) {
 *     this.applicationUnderTest = applicationUnderTest;
 *   }
 * }
 * }</pre>
 *
 * <p>{@link Application#SHARED} (the default) is the one application of the whole run ({@link SharedApplication},
 * TypeScript's single server for the end-to-end suites). {@link Application#OWN} gives the annotated class a fresh
 * application of its own, started before its first test and stopped after its last one, for suites that restart, kill
 * or otherwise disturb the process; its {@link AppProcess} is named after the class.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("end-to-end")
@ExtendWith(EndToEndExtension.class)
public @interface EndToEnd {

  /**
   * @return which application the suite uses
   */
  Application value() default Application.SHARED;

  /** Which application an {@link EndToEnd} suite uses. */
  enum Application {
    /** The application shared by every suite of the run. */
    SHARED,
    /** A fresh application for the annotated class (local provider only). */
    OWN
  }
}
