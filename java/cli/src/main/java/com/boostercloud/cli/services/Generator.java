package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Filenames;
import com.boostercloud.cli.services.generator.target.FileDir;
import com.boostercloud.cli.services.generator.target.Target;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renders resource templates into files ({@code services/generator.ts}). */
public final class Generator {
  private Generator() {}

  /**
   * Generates a resource file: asks before overwriting an existing file, validates the resource name, renders the
   * template with the target information and writes the file (creating missing directories).
   *
   * @param ctx the CLI context
   * @param target what to generate
   * @param <T> the type of the view
   * @throws CliException when the user refuses to overwrite, the name is invalid, or the file cannot be written
   */
  public static <T> void generate(CliContext ctx, Target<T> target) {
    ProjectChecker.checkResourceExists(ctx, target.name(), target.placementDir(), target.extension());
    Filenames.checkResourceNameIsValid(target.name());
    String rendered = Templates.render(target.template(), target.info());
    Path renderPath = filePath(ctx, target);
    try {
      Files.createDirectories(renderPath.getParent());
      Files.writeString(renderPath, rendered, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CliException("Could not write " + renderPath + ": " + e, e);
    }
  }

  /**
   * The template of a resource: {@code <cwd>/stubs/<name>.stub} when the user has customised it, otherwise the
   * built-in one. It is read every time.
   *
   * @param ctx the CLI context
   * @param name the template
   * @return the template text
   */
  public static String template(CliContext ctx, TemplateType name) {
    String fileName = name.fileName();
    Path stubFile = StubPublisher.resourceStubFilePath(ctx.cwd(), fileName);
    if (StubPublisher.checkStubsFolderExists(ctx.cwd()) && StubPublisher.checkResourceStubFileExists(stubFile)) {
      try {
        return Files.readString(stubFile, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new CliException("Could not read the custom template " + stubFile + ": " + e, e);
      }
    }
    return Templates.bundled(fileName);
  }

  /**
   * The file of a resource. Java files are named after their class: {@code <cwd>/<placementDir>/<name><ext>}
   * (TypeScript dasherizes the name).
   *
   * @param ctx the CLI context
   * @param target the file location
   * @return the absolute path
   */
  public static Path filePath(CliContext ctx, FileDir target) {
    return ctx.cwd().resolve(target.placementDir()).resolve(target.name() + target.extension());
  }

  /**
   * The resource folder name of a path, like Node's {@code path.parse(p).name}: {@code src/main/java/x/readmodels}
   * gives {@code readmodels}.
   *
   * @param resourcePath a path
   * @return its last segment without extension
   */
  public static String getResourceType(String resourcePath) {
    Path fileName = Path.of(resourcePath).getFileName();
    if (fileName == null) {
      return "";
    }
    String name = fileName.toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
