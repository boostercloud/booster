package com.boostercloud.cli.services;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Errors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Locations of the resource templates and publication of the built-in ones into {@code <project>/stubs}
 * ({@code services/stub-publisher.ts}). Templates in {@code <project>/stubs} override the built-in ones.
 *
 * <p>The built-in templates are classpath resources inside the CLI jar instead of files next to the compiled
 * code, so they are listed from {@link TemplateType} instead of reading a directory.
 */
public final class StubPublisher {
  /** The folder, relative to the project, with the user's custom templates. */
  public static final String STUBS_FOLDER = "stubs";

  /** Classpath folder of the built-in templates ({@code resourceTemplatesPath}). */
  public static final String RESOURCE_TEMPLATES_PATH = Templates.TEMPLATES_ROOT;

  private StubPublisher() {}

  /**
   * @param cwd the project directory
   * @param fileName a stub file name
   * @return {@code <cwd>/stubs/<fileName>}
   */
  public static Path resourceStubFilePath(Path cwd, String fileName) {
    return cwd.resolve(STUBS_FOLDER).resolve(fileName);
  }

  /**
   * @param fileName a stub file name
   * @return the classpath resource of the built-in template
   */
  public static String resourceTemplateFilePath(String fileName) {
    return RESOURCE_TEMPLATES_PATH + fileName;
  }

  /**
   * @param cwd the project directory
   * @return whether {@code <cwd>/stubs} exists
   */
  public static boolean checkStubsFolderExists(Path cwd) {
    return Files.exists(cwd.resolve(STUBS_FOLDER));
  }

  /**
   * @param filePath a file
   * @return whether it exists
   */
  public static boolean checkResourceStubFileExists(Path filePath) {
    return Files.exists(filePath);
  }

  /**
   * Creates {@code <cwd>/stubs} (non-recursively, like {@code mkdirSync}).
   *
   * @param cwd the project directory
   * @throws CliException when it cannot be created
   */
  public static void createStubsFolder(Path cwd) {
    try {
      Files.createDirectory(cwd.resolve(STUBS_FOLDER));
    } catch (IOException e) {
      throw new CliException("Could not create the stubs folder: " + e, e);
    }
  }

  /**
   * The names of the built-in templates.
   *
   * @return {@code command.stub}, {@code query.stub}...
   */
  public static List<String> resourceTemplateFileNames() {
    return Arrays.stream(TemplateType.values()).map(TemplateType::fileName).toList();
  }

  /**
   * Maps the built-in template of every file name that contains {@code .stub} to its destination in
   * {@code <cwd>/stubs}.
   *
   * @param cwd the project directory
   * @param fileNames candidate file names
   * @return classpath resource → destination file, in order
   */
  public static Map<String, Path> createTemplateFileMap(Path cwd, Collection<String> fileNames) {
    Map<String, Path> files = new LinkedHashMap<>();
    for (String name : fileNames) {
      if (name.contains(".stub")) {
        files.put(resourceTemplateFilePath(name), cwd.resolve(STUBS_FOLDER).resolve(name));
      }
    }
    return files;
  }

  /**
   * Copies every built-in template into {@code <cwd>/stubs}, overwriting existing files.
   *
   * @param cwd the project directory
   * @throws CliException {@code Error when publishing stubs} when a file cannot be copied
   */
  public static void publishStubFiles(Path cwd) {
    Map<String, Path> templateFilesMap = createTemplateFileMap(cwd, resourceTemplateFileNames());
    try {
      for (Map.Entry<String, Path> entry : templateFilesMap.entrySet()) {
        copyStubFile(entry.getKey(), entry.getValue());
      }
    } catch (RuntimeException e) {
      throw Errors.wrapExecError(e, "Error when publishing stubs");
    }
  }

  static void copyStubFile(String from, Path to) {
    String content = Templates.bundled(from.substring(RESOURCE_TEMPLATES_PATH.length()));
    try {
      Files.writeString(to, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CliException("Could not write " + to + ": " + e, e);
    }
  }
}
