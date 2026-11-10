package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Port of {@code sensor/health/health-indicators/booster-version.ts} ({@code boosterVersion(config)}): the version of
 * the framework used by the application, {@code ""} when unknown; never throws.
 *
 * <p>TypeScript reads {@code dependencies["@boostercloud/framework-core"]} from the project's {@code package.json}. A
 * Java application has no such file: the version is the one of the framework-core artifact on the classpath, taken
 * from its jar manifest ({@code Implementation-Version}) or, failing that, from the Maven descriptor
 * {@code META-INF/maven/com.boostercloud/booster-framework-core/pom.properties}. When neither is available (for
 * example when running from {@code target/classes}) the warning {@code Could not get Booster Version} is logged and
 * {@code ""} is returned; an unexpected error logs
 * {@code There was an error when calculating the booster version the application} and returns {@code ""} too.
 */
public final class BoosterVersion {

  /** The Maven descriptor of the framework-core artifact. */
  static final String POM_PROPERTIES = "/META-INF/maven/com.boostercloud/booster-framework-core/pom.properties";

  private BoosterVersion() {}

  /**
   * @param config the configuration
   * @return the framework version, or {@code ""}
   */
  public static String boosterVersion(BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "boosterVersion");
    try {
      String version = frameworkVersion();
      if (version == null || version.isEmpty()) {
        logger.warn("Could not get Booster Version");
        return "";
      }
      return version;
    } catch (RuntimeException | IOException e) {
      logger.warn("There was an error when calculating the booster version the application", e);
      return "";
    }
  }

  /**
   * The TypeScript transformation of a {@code package.json} dependency specification: removes the first
   * {@code workspace:}, {@code ^} and {@code .tgz}, splits on {@code -} and keeps the last part
   * ({@code ^3.4.1} → {@code 3.4.1}; {@code file:../boostercloud-framework-core-3.4.1.tgz} → {@code 3.4.1}). Kept
   * for applications that pass npm-style specifications; Maven versions are returned unchanged by
   * {@link #boosterVersion(BoosterConfig)}.
   *
   * @param dependencySpecification the specification
   * @return the version
   */
  public static String fromDependencySpecification(String dependencySpecification) {
    String[] versionParts = dependencySpecification.replaceFirst("workspace:", "").replaceFirst("\\^", "")
        .replaceFirst("\\.tgz", "").split("-", -1);
    return versionParts[versionParts.length - 1];
  }

  private static String frameworkVersion() throws IOException {
    Package frameworkPackage = BoosterVersion.class.getPackage();
    String implementationVersion = frameworkPackage == null ? null : frameworkPackage.getImplementationVersion();
    if (implementationVersion != null && !implementationVersion.isEmpty()) {
      return implementationVersion;
    }
    try (InputStream pomProperties = BoosterVersion.class.getResourceAsStream(POM_PROPERTIES)) {
      if (pomProperties == null) {
        return null;
      }
      Properties properties = new Properties();
      properties.load(pomProperties);
      return properties.getProperty("version");
    }
  }
}
