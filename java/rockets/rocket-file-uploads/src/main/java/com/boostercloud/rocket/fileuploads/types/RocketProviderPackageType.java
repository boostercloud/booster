package com.boostercloud.rocket.fileuploads.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The provider runtime library the rocket uses ({@code RocketProviderPackageType} of {@code rocket-files-params.ts},
 * the union {@code '@boostercloud/rocket-file-uploads-azure' | '@boostercloud/rocket-file-uploads-local'}).
 *
 * <p>The value written in the rocket descriptor parameters ({@code rocketProviderPackage}) is the TypeScript npm name,
 * so the framework derives the same rocket health key ({@code rocket-file-uploads}) and the same Azure
 * {@code ROCKET_PACKAGE_MAPPING} entry as in TypeScript. Each constant also names the Java package that holds the
 * runtime library ({@code <javaPackage>.RocketFilesLibrary}), which {@code core} loads where TypeScript calls
 * {@code require(rocketProviderPackage)}.
 */
public enum RocketProviderPackageType {
  /** {@code @boostercloud/rocket-file-uploads-azure}: Azure Blob Storage. */
  AZURE("@boostercloud/rocket-file-uploads-azure", "com.boostercloud.rocket.fileuploads.azure"),
  /** {@code @boostercloud/rocket-file-uploads-local}: the local file system. */
  LOCAL("@boostercloud/rocket-file-uploads-local", "com.boostercloud.rocket.fileuploads.local");

  /** The simple name of the runtime library class in {@link #javaPackage()}. */
  public static final String LIBRARY_CLASS = "RocketFilesLibrary";

  private final String packageName;
  private final String javaPackage;

  RocketProviderPackageType(String packageName, String javaPackage) {
    this.packageName = packageName;
    this.javaPackage = javaPackage;
  }

  /**
   * @return the TypeScript npm package name (the JSON value)
   */
  @JsonValue
  public String packageName() {
    return packageName;
  }

  /**
   * @return the Java package of the runtime library
   */
  public String javaPackage() {
    return javaPackage;
  }

  /**
   * @return the fully qualified name of the runtime library class
   */
  public String libraryClassName() {
    return javaPackage + "." + LIBRARY_CLASS;
  }

  /**
   * @param value an npm package name, a Java package name or a constant name
   * @return the matching constant
   * @throws IllegalArgumentException for any other value
   */
  @JsonCreator
  public static RocketProviderPackageType fromPackageName(String value) {
    for (RocketProviderPackageType type : values()) {
      if (type.packageName.equals(value) || type.javaPackage.equals(value) || type.name().equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown rocket provider package " + value);
  }

  @Override
  public String toString() {
    return packageName;
  }
}
