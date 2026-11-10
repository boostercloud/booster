package com.boostercloud.cli.services;

import com.boostercloud.cli.services.java.JavaNames;

/**
 * The answers that describe a new project ({@code ProjectInitializerConfig}).
 *
 * @param projectName the project (and Maven artifact) name
 * @param description a short description
 * @param version the initial version
 * @param author who is writing it
 * @param homepage the website
 * @param license the license
 * @param repository the repository URL
 * @param providerPackageName the Java package of the provider of the {@code production} environment; empty or the
 *     local provider package for a local-only project
 * @param boosterVersion the CLI version that created the project
 * @param defaults whether the project was created with {@code --default} ({@code default} in TypeScript, a Java
 *     keyword)
 * @param skipInstall whether to skip resolving the dependencies
 * @param skipGit whether to skip the git repository initialization
 * @param groupId the Maven group (Java only); the base package is {@code <groupId>.<project name>}
 */
public record ProjectInitializerConfig(
    String projectName,
    String description,
    String version,
    String author,
    String homepage,
    String license,
    String repository,
    String providerPackageName,
    String boosterVersion,
    boolean defaults,
    boolean skipInstall,
    boolean skipGit,
    String groupId) {

  /** The group used when none is given. */
  public static final String DEFAULT_GROUP_ID = "com.example";

  /**
   * The base Java package of the project: the group followed by the project name reduced to lower-case letters
   * and digits ({@code com.example} and {@code my-store} give {@code com.example.mystore}).
   *
   * @return the base package
   */
  public String basePackage() {
    return groupId + "." + JavaNames.packageSegment(projectName);
  }

  /** @return whether a {@code production} environment is configured with {@link #providerPackageName()} */
  public boolean hasProductionEnvironment() {
    return providerPackageName != null
        && !providerPackageName.isEmpty()
        && !providerPackageName.equals(FrameworkApi.LOCAL_PROVIDER_PACKAGE);
  }

  /** @return whether the production environment uses the Azure provider */
  public boolean usesAzure() {
    return FrameworkApi.AZURE_PROVIDER_PACKAGE.equals(providerPackageName);
  }
}
