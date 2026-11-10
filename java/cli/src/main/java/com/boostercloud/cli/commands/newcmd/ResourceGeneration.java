package com.boostercloud.cli.commands.newcmd;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.services.Generator;
import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.services.generator.target.ImportDeclaration;
import com.boostercloud.cli.services.generator.target.Target;
import com.boostercloud.cli.services.java.JavaNames;
import com.boostercloud.cli.services.java.JavaTypes;
import com.boostercloud.cli.services.java.ProjectIndex;
import com.boostercloud.cli.services.project.ProjectLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the Java view of a resource template and generates the file. Shared by the {@code new:*} generators.
 *
 * <p>The view contains what the TypeScript templates receive ({@code name}, {@code fields}, {@code events},
 * {@code projections}, {@code event}, {@code imports}) adapted to Java:
 *
 * <ul>
 *   <li>{@code packageName}: the package of the resource ({@code <base package>.<sub-package>});
 *   <li>{@code imports}: one entry per imported class ({@code fqn}, {@code packagePath},
 *       {@code commaSeparatedComponents}), sorted, without duplicates nor same-package classes, and including the
 *       classes needed by the field types; {@code hasImports} tells whether there is any;
 *   <li>{@code fields}: {@code name}, {@code type} (the Java type), {@code rawType} (as typed) and {@code last};
 *       {@code hasFields} tells whether there is any.
 * </ul>
 */
final class ResourceGeneration {
  /** Extension of the generated files. */
  static final String EXTENSION = ".java";

  private ResourceGeneration() {}

  /**
   * Generates a resource.
   *
   * @param ctx the CLI context
   * @param subpackage the resource package, e.g. {@code commands}
   * @param templateType the template
   * @param imports the framework and project imports of the resource
   * @param info the joined parser results ({@code name}, {@code fields}...)
   */
  @SuppressWarnings("unchecked")
  static void generate(
      CliContext ctx,
      String subpackage,
      TemplateType templateType,
      List<ImportDeclaration> imports,
      Map<String, Object> info) {
    ProjectLayout layout = ProjectLayout.of(ctx.cwd());
    String packageName = layout.packageName(subpackage);
    String name = (String) info.get("name");

    Set<String> qualifiedImports = new LinkedHashSet<>();
    imports.forEach(declaration -> qualifiedImports.addAll(declaration.qualifiedNames()));

    List<Map<String, Object>> rawFields = (List<Map<String, Object>>) info.getOrDefault("fields", List.of());
    List<Map<String, Object>> fields = new ArrayList<>();
    Set<String> unresolved = new LinkedHashSet<>();
    for (int i = 0; i < rawFields.size(); i++) {
      String rawType = String.valueOf(rawFields.get(i).get("type"));
      JavaTypes.JavaType javaType = JavaTypes.toJava(rawType);
      qualifiedImports.addAll(javaType.imports());
      unresolved.addAll(javaType.unresolvedSimpleNames());
      Map<String, Object> field = new LinkedHashMap<>();
      field.put("name", rawFields.get(i).get("name"));
      field.put("type", javaType.rendered());
      field.put("rawType", rawType);
      field.put("last", i == rawFields.size() - 1);
      fields.add(field);
    }
    if (!unresolved.isEmpty()) {
      ProjectIndex index = ProjectIndex.scan(layout.absoluteSourceDirectory());
      for (String simpleName : unresolved) {
        if (!simpleName.equals(name)) {
          index.uniqueQualifiedName(simpleName).ifPresent(qualifiedImports::add);
        }
      }
    }

    Set<String> sortedImports = new TreeSet<>();
    for (String qualifiedName : qualifiedImports) {
      String importPackage = JavaNames.packageOf(qualifiedName);
      if (!importPackage.equals(packageName) && !importPackage.equals("java.lang") && !importPackage.isEmpty()) {
        sortedImports.add(qualifiedName);
      }
    }
    List<Map<String, Object>> importViews = new ArrayList<>();
    for (String qualifiedName : sortedImports) {
      Map<String, Object> importView = new LinkedHashMap<>();
      importView.put("fqn", qualifiedName);
      importView.put("packagePath", JavaNames.packageOf(qualifiedName));
      importView.put("commaSeparatedComponents", JavaNames.simpleNameOf(qualifiedName));
      importViews.add(importView);
    }

    Map<String, Object> view = new LinkedHashMap<>(info);
    view.put("packageName", packageName);
    view.put("imports", importViews);
    view.put("hasImports", !importViews.isEmpty());
    view.put("fields", fields);
    view.put("hasFields", !fields.isEmpty());

    Generator.generate(
        ctx,
        new Target<>(name, EXTENSION, layout.packageDirectory(subpackage).toString(), Generator.template(ctx, templateType), view));
  }

  /**
   * The package of another kind of resource of the project in the current directory.
   *
   * @param ctx the CLI context
   * @param subpackage e.g. {@code events}
   * @return {@code <base package>.<subpackage>}
   */
  static String projectPackage(CliContext ctx, String subpackage) {
    return ProjectLayout.of(ctx.cwd()).packageName(subpackage);
  }
}
