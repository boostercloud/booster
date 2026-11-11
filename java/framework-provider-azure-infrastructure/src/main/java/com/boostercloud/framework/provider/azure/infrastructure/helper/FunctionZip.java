package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.azure.WebSiteManagementClient;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Port of {@code helper/function-zip.ts}: builds the function app packages and uploads them through Kudu.
 *
 * <p>TypeScript packages a Node app ({@code .deploy/**} + generated {@code functions.js}, {@code host.json} and
 * {@code package.json}). Java packages a Java worker app ({@link FunctionAppLayout}): the application jar built from
 * the class path directories, the class path jars under {@code lib/}, the assets, one {@code <function>/function.json}
 * per function and {@code host.json}. Packages are written to a temporary file (deflate level 9, entries with a fixed
 * timestamp so the bytes are reproducible) and copied to {@code <project root>/<fileName>}.
 *
 * <table>
 *   <caption>Packages</caption>
 *   <tr><th>Method</th><th>Functions</th><th>{@code host.json}</th><th>{@code name}</th></tr>
 *   <tr><td>{@link #copyZip}</td><td>{@link FunctionsCodeGenerator#generateFunctionsCode()}</td>
 *       <td>custom or default</td><td>{@code functions}</td></tr>
 *   <tr><td>{@link #copyConsumerZip}</td><td>{@link FunctionsCodeGenerator#generateConsumerFunctionsCode()}</td>
 *       <td>custom or default</td><td>{@code consumer-functions}</td></tr>
 *   <tr><td>{@link #copyZipV4ForRocket}</td><td>the rocket's</td><td>custom or default</td>
 *       <td>{@code functions}</td></tr>
 *   <tr><td>{@link #copyZipLegacy}</td><td>the rocket's</td>
 *       <td>custom, or default unless the sources have one</td><td>{@code functions}</td></tr>
 *   <tr><td>{@link #copyBaseZip}</td><td>{@code baseWebPubSubBinding} (subscriptions only)</td><td>default</td>
 *       <td>(a path)</td></tr>
 * </table>
 */
public final class FunctionZip {

  /** The default {@code host.json} ({@code JSON.stringify(hostConfig, null, 2)}). */
  public static final String DEFAULT_HOST_JSON = JsonText.stringify(defaultHostConfig(), 2);

  /** The folder with the files of the WebPubSub bootstrap package ({@code .deploy-base}). */
  public static final String DEPLOY_BASE_DIRECTORY = ".deploy-base";

  /** The file name (without extension) of the WebPubSub bootstrap package. */
  public static final String BASE_ZIP_NAME = "baseWebPubSubBinding";

  private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0, 0);

  private final BoosterConfig config;
  private final InfrastructureContext context;

  /**
   * @param config the configuration
   * @param context the infrastructure context (project root, sources, HTTP transport, credentials)
   */
  public FunctionZip(BoosterConfig config, InfrastructureContext context) {
    this.config = Objects.requireNonNull(config, "config");
    this.context = Objects.requireNonNull(context, "context");
  }

  /**
   * @return the default host configuration: version 2.0 with the extension bundle {@code [4.*, 5.0.0)}
   */
  public static Map<String, Object> defaultHostConfig() {
    Map<String, Object> bundle = new LinkedHashMap<>();
    bundle.put("id", "Microsoft.Azure.Functions.ExtensionBundle");
    bundle.put("version", "[4.*, 5.0.0)");
    Map<String, Object> host = new LinkedHashMap<>();
    host.put("version", "2.0");
    host.put("extensionBundle", bundle);
    return host;
  }

  /**
   * {@code deployZip(config, functionAppName, resourceGroupName, zipResource)}: reads the publishing credentials of the
   * function app ({@code beginListPublishingCredentials}) and {@code POST}s the package to
   * {@code https://<credentials.name>.scm.azurewebsites.net/api/zipDeploy?isAsync=true} with HTTP basic
   * authentication. As in TypeScript the host comes from the credentials' {@code name}, the response status is not
   * checked and the asynchronous deployment is not awaited.
   *
   * @param functionAppName the function app
   * @param resourceGroupName its resource group
   * @param zipResource the package
   * @return the package
   */
  public ZipResource deployZip(String functionAppName, String resourceGroupName, ZipResource zipResource) {
    Logger logger = Loggers.getLogger(config, "function-zip#deployZip");
    logger.info("Uploading zip file");
    WebSiteManagementClient websites = context.webSiteManagementClient();
    WebSiteManagementClient.User credentials = websites.listPublishingCredentials(resourceGroupName, functionAppName);
    deployFunctionPackage(zipResource.path(), orEmpty(credentials.publishingUserName()),
        orEmpty(credentials.publishingPassword()), orEmpty(credentials.name()));
    logger.info("Zip file uploaded");
    return zipResource;
  }

  private void deployFunctionPackage(Path packagePath, String username, String password, String functionAppName) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Basic " + Base64.getEncoder()
        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
    context.httpTransport().send(new HttpTransport.Request("POST", URI.create(getZipDeployUrl(functionAppName)),
        headers, packagePath, null));
  }

  /**
   * @param functionAppName the function app
   * @return {@code https://<name>.scm.azurewebsites.net/api/zipDeploy?isAsync=true}
   */
  public static String getZipDeployUrl(String functionAppName) {
    return "https://" + functionAppName + ".scm.azurewebsites.net/api/zipDeploy?isAsync=true";
  }

  /**
   * {@code copyZip(config, fileName, hostJsonPath)}: the main function app package.
   *
   * @param fileName the package file name ({@code functionApp.zip})
   * @param hostJsonPath a custom {@code host.json}, or {@code null}
   * @return the package
   */
  public ZipResource copyZip(String fileName, Path hostJsonPath) {
    Logger logger = Loggers.getLogger(config, "function-zip#createZipV4");
    logger.info("Creating Azure Functions v4 ZIP package");
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(config).generateFunctionsCode();
    Path zip = createZip(fileName, archive -> {
      appendProjectFiles(archive, true);
      appendFunctionDefinitions(archive, functions);
      appendHostConfig(archive, hostJsonPath);
    });
    logger.info("Azure Functions v4 ZIP package created");
    return copyToProject(zip, fileName, "functions");
  }

  /**
   * {@code copyConsumerZip(config, fileName, hostJsonPath)}: the event stream consumer package (its functions only
   * when event streaming is enabled).
   *
   * @param fileName the package file name ({@code consumerFunctionApp.zip})
   * @param hostJsonPath a custom {@code host.json}, or {@code null}
   * @return the package
   */
  public ZipResource copyConsumerZip(String fileName, Path hostJsonPath) {
    Logger logger = Loggers.getLogger(config, "function-zip#createConsumerZipV4");
    logger.info("Creating Azure Consumer Functions v4 ZIP package");
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(config).generateConsumerFunctionsCode();
    Path zip = createZip(fileName, archive -> {
      appendProjectFiles(archive, true);
      appendFunctionDefinitions(archive, functions);
      appendHostConfig(archive, hostJsonPath);
    });
    logger.info("Azure Consumer Functions v4 ZIP package created");
    return copyToProject(zip, fileName, "consumer-functions");
  }

  /**
   * {@code copyZipV4ForRocket(config, functionCode, fileName, hostJsonPath)}: a rocket package with the layout of the
   * main package and the rocket's functions.
   *
   * @param functions the rocket's functions (the Java counterpart of {@code functionCode})
   * @param fileName the package file name ({@code rocket_<app>.zip})
   * @param hostJsonPath a custom {@code host.json}, or {@code null}
   * @return the package
   */
  public ZipResource copyZipV4ForRocket(List<FunctionDefinition> functions, String fileName, Path hostJsonPath) {
    Logger logger = Loggers.getLogger(config, "function-zip#createZipV4ForRocket");
    logger.info("Creating Azure Functions v4 ZIP package for rocket");
    Path zip = createZip(fileName, archive -> {
      appendProjectFiles(archive, true);
      appendFunctionDefinitions(archive, functions);
      appendHostConfig(archive, hostJsonPath);
    });
    logger.info("Azure Functions v4 ZIP package for rocket created");
    return copyToProject(zip, fileName, "functions");
  }

  /**
   * {@code copyZipLegacy(functionDefinitions, fileName, hostJsonPath)} (deprecated, for {@code mountCode} and
   * {@code mountFunctions} rockets): every source file, the definitions and {@code host.json} (the custom one, or the
   * default one only when the sources have no {@code host.json}).
   *
   * @param functionDefinitions the functions
   * @param fileName the package file name
   * @param hostJsonPath a custom {@code host.json}, or {@code null}
   * @return the package
   * @deprecated rockets should return {@code mountFunctionsV4} definitions
   */
  @Deprecated
  public ZipResource copyZipLegacy(List<FunctionDefinition> functionDefinitions, String fileName, Path hostJsonPath) {
    Path zip = createZip(fileName, archive -> {
      boolean sourcesHaveHostJson = appendProjectFiles(archive, false);
      appendFunctionDefinitions(archive, functionDefinitions);
      if (hostJsonPath != null) {
        appendCustomHostConfig(archive, hostJsonPath);
      } else if (!sourcesHaveHostJson) {
        archive.add(FunctionAppLayout.HOST_JSON, DEFAULT_HOST_JSON);
      }
    });
    return copyToProject(zip, fileName, "functions");
  }

  /**
   * {@code copyBaseZip(config)}: the WebPubSub bootstrap package Terraform deploys with the main function app: the
   * files of {@code .deploy-base} (when it exists), with subscriptions the {@code baseWebPubSubBinding} function (a
   * Web PubSub user {@code message} trigger with {@code connection: WebPubSubConnectionString} on a generated class,
   * see {@link BaseWebPubSubBinding}) and the default {@code host.json}.
   *
   * @return {@code <project root>/baseWebPubSubBinding.zip}
   */
  public Path copyBaseZip() {
    String fileName = BASE_ZIP_NAME + ".zip";
    Path zip = createZip(fileName, archive -> {
      Path base = context.projectRoot().resolve(DEPLOY_BASE_DIRECTORY);
      if (Files.isDirectory(base)) {
        appendDirectory(archive, base, "", true);
      }
      if (config.enableSubscriptions) {
        archive.add(BaseWebPubSubBinding.JAR_NAME, baseJar());
        FunctionDefinition definition = new FunctionDefinition(BaseWebPubSubBinding.FUNCTION_NAME, new FunctionConfig(
            List.of(Binding.webPubSubTrigger("request", "booster", "user", "message", "WebPubSubConnectionString")),
            "../" + BaseWebPubSubBinding.JAR_NAME, BaseWebPubSubBinding.entryPoint()));
        appendFunctionDefinitions(archive, List.of(definition));
      }
      archive.add(FunctionAppLayout.HOST_JSON, DEFAULT_HOST_JSON);
    });
    return copyToProject(zip, fileName, "base").path();
  }

  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Adds the application jar, {@code lib/} and the assets.
   *
   * @param archive the archive
   * @param ignoreRootHostJson whether a root {@code host.json} in the assets is skipped (it is generated)
   * @return whether the sources contain a root {@code host.json}
   */
  private boolean appendProjectFiles(Archive archive, boolean ignoreRootHostJson) {
    DeploySources sources = context.deploySources(config);
    archive.add(FunctionAppLayout.appJarName(config), applicationJar(sources.classDirectories()));
    for (Path library : sources.libraries()) {
      archive.addFile(FunctionAppLayout.LIB_DIRECTORY + "/" + library.getFileName(), library);
    }
    boolean hasHostJson = false;
    for (Path asset : sources.assets()) {
      Path absolute = sources.projectRoot().resolve(asset).normalize();
      String relative = sources.projectRoot().toAbsolutePath().normalize()
          .relativize(absolute.toAbsolutePath().normalize()).toString().replace('\\', '/');
      if (Files.isDirectory(absolute)) {
        hasHostJson |= appendDirectory(archive, absolute, relative + "/", ignoreRootHostJson);
      } else if (Files.isRegularFile(absolute)) {
        if (relative.equals(FunctionAppLayout.HOST_JSON)) {
          hasHostJson = true;
          if (ignoreRootHostJson) {
            continue;
          }
        }
        archive.addFile(relative, absolute);
      }
    }
    return hasHostJson;
  }

  private static boolean appendDirectory(Archive archive, Path directory, String prefix, boolean ignoreRootHostJson) {
    boolean hasHostJson = false;
    for (Path file : files(directory)) {
      String name = prefix + directory.relativize(file).toString().replace('\\', '/');
      if (name.equals(FunctionAppLayout.HOST_JSON)) {
        hasHostJson = true;
        if (ignoreRootHostJson) {
          continue;
        }
      }
      archive.addFile(name, file);
    }
    return hasHostJson;
  }

  private void appendFunctionDefinitions(Archive archive, List<FunctionDefinition> definitions) {
    for (FunctionDefinition definition : definitions) {
      archive.add(definition.name() + "/" + FunctionAppLayout.FUNCTION_JSON, functionJson(definition.config()));
    }
  }

  /**
   * @param config a function configuration
   * @return its {@code function.json} text ({@code JSON.stringify(config, null, 2)})
   */
  public static String functionJson(FunctionConfig config) {
    return JsonText.stringify(config.toRaw(), 2);
  }

  private static void appendHostConfig(Archive archive, Path hostJsonPath) {
    if (hostJsonPath != null) {
      appendCustomHostConfig(archive, hostJsonPath);
    } else {
      archive.add(FunctionAppLayout.HOST_JSON, DEFAULT_HOST_JSON);
    }
  }

  private static void appendCustomHostConfig(Archive archive, Path hostJsonPath) {
    String hostJson;
    try {
      hostJson = Files.readString(hostJsonPath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + hostJsonPath + ": " + e.getMessage(), e);
    }
    if (!hostJson.isEmpty()) {
      archive.add(FunctionAppLayout.HOST_JSON, hostJson);
    }
  }

  /**
   * Packs class directories into a jar (a manifest first, then every file in path order; the first directory wins on
   * duplicates).
   *
   * @param classDirectories the directories
   * @return the jar bytes
   */
  static byte[] applicationJar(List<Path> classDirectories) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
      jar.setLevel(Deflater.BEST_COMPRESSION);
      Set<String> names = new HashSet<>();
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().put(new Attributes.Name("Created-By"), "Booster Framework");
      putEntry(jar, "META-INF/", null);
      names.add("META-INF/");
      ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
      manifest.write(manifestBytes);
      putEntry(jar, JarFile.MANIFEST_NAME, manifestBytes.toByteArray());
      names.add(JarFile.MANIFEST_NAME);
      for (Path directory : classDirectories) {
        for (Path file : files(directory)) {
          String name = directory.relativize(file).toString().replace('\\', '/');
          if (names.add(name)) {
            putEntry(jar, name, Files.readAllBytes(file));
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not build the application jar: " + e.getMessage(), e);
    }
    return bytes.toByteArray();
  }

  private static byte[] baseJar() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
      jar.setLevel(Deflater.BEST_COMPRESSION);
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      putEntry(jar, "META-INF/", null);
      ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
      manifest.write(manifestBytes);
      putEntry(jar, JarFile.MANIFEST_NAME, manifestBytes.toByteArray());
      putEntry(jar, BaseWebPubSubBinding.classEntryName(), BaseWebPubSubBinding.classFile());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not build the base jar: " + e.getMessage(), e);
    }
    return bytes.toByteArray();
  }

  private static void putEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    entry.setTimeLocal(ENTRY_TIME);
    zip.putNextEntry(entry);
    if (content != null) {
      zip.write(content);
    }
    zip.closeEntry();
  }

  private static List<Path> files(Path directory) {
    try (Stream<Path> walk = Files.walk(directory)) {
      return walk.filter(Files::isRegularFile)
          .sorted((a, b) -> directory.relativize(a).toString().replace('\\', '/')
              .compareTo(directory.relativize(b).toString().replace('\\', '/')))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list " + directory + ": " + e.getMessage(), e);
    }
  }

  private Path createZip(String fileName, ArchiveWriter writer) {
    Path temporary;
    try {
      temporary = Files.createTempFile("booster-" + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "-", ".zip");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create a temporary file: " + e.getMessage(), e);
    }
    try (OutputStream out = Files.newOutputStream(temporary); ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.setLevel(Deflater.BEST_COMPRESSION);
      writer.write(new Archive(zip));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + fileName + ": " + e.getMessage(), e);
    }
    return temporary;
  }

  private ZipResource copyToProject(Path temporary, String fileName, String name) {
    Path destination = context.projectRoot().resolve(fileName);
    try {
      Files.createDirectories(context.projectRoot());
      Files.copy(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      Files.deleteIfExists(temporary);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not copy " + fileName + " to " + destination + ": " + e.getMessage(), e);
    }
    return new ZipResource(destination, fileName, name);
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  @FunctionalInterface
  private interface ArchiveWriter {
    void write(Archive archive) throws IOException;
  }

  /** The entries of a package being written; the first entry with a name wins. */
  private static final class Archive {
    private final ZipOutputStream zip;
    private final Set<String> names = new HashSet<>();

    Archive(ZipOutputStream zip) {
      this.zip = zip;
    }

    void add(String name, String content) {
      add(name, content.getBytes(StandardCharsets.UTF_8));
    }

    void add(String name, byte[] content) {
      if (!names.add(name)) {
        return;
      }
      try {
        putEntry(zip, name, content);
      } catch (IOException e) {
        throw new UncheckedIOException("Could not add " + name + ": " + e.getMessage(), e);
      }
    }

    void addFile(String name, Path file) {
      if (!names.add(name)) {
        return;
      }
      try (InputStream in = Files.newInputStream(file)) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(ENTRY_TIME);
        zip.putNextEntry(entry);
        in.transferTo(zip);
        zip.closeEntry();
      } catch (IOException e) {
        throw new UncheckedIOException("Could not add " + file + ": " + e.getMessage(), e);
      }
    }
  }
}
