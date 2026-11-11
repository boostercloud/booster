package com.boostercloud.cli.services;

import com.boostercloud.cli.common.CliException;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Mustache rendering ({@code Mustache.render} of mustache.js in TypeScript) and access to the templates bundled
 * with the CLI.
 *
 * <p>Rendering rules the templates rely on: sections repeat once per list element and are skipped for empty lists
 * and {@code false}; a line holding only a section tag is removed; names are looked up in the current element first
 * and then in the enclosing view; {@code {{x}}} escapes HTML/XML special characters and {@code {{{x}}}} inserts the
 * raw text. Note that mustache.java also drops a line break that immediately follows a section opening tag.
 */
public final class Templates {
  /** Classpath folder of the bundled templates. */
  public static final String TEMPLATES_ROOT = "com/boostercloud/cli/templates/";

  private Templates() {}

  /**
   * Renders a template.
   *
   * @param template the Mustache template
   * @param view the values (maps, lists, strings, booleans)
   * @return the rendered text
   * @throws CliException when the template is malformed
   */
  public static String render(String template, Object view) {
    try {
      Mustache mustache = new DefaultMustacheFactory().compile(new StringReader(template), "template");
      StringWriter writer = new StringWriter();
      mustache.execute(writer, view).flush();
      return writer.toString();
    } catch (IOException | RuntimeException e) {
      throw new CliException("Could not render the template: " + e.getMessage(), e);
    }
  }

  /**
   * Reads a template bundled with the CLI.
   *
   * @param relativePath the path under {@value #TEMPLATES_ROOT}, e.g. {@code command.stub}
   * @return the template text
   * @throws CliException when the template does not exist
   */
  public static String bundled(String relativePath) {
    String resource = TEMPLATES_ROOT + relativePath;
    try (InputStream in = Templates.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new CliException("The template " + resource + " is not bundled with the CLI");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CliException("Could not read the template " + resource + ": " + e.getMessage(), e);
    }
  }
}
