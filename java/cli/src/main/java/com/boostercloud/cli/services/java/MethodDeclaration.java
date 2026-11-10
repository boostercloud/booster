package com.boostercloud.cli.services.java;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The structure of a method to add to a class, the Java counterpart of ts-morph's
 * {@code OptionalKind<MethodDeclarationStructure>} with the same keys.
 *
 * @param decorators the annotations, e.g. {@code @Reduces(PostCreated.class)}
 * @param scope the visibility ({@code public})
 * @param isStatic whether the method is static
 * @param name the method name
 * @param parameters the parameters
 * @param returnType the return type
 * @param statements the statements of the body, one per line
 */
public record MethodDeclaration(
    List<Decorator> decorators,
    String scope,
    boolean isStatic,
    String name,
    List<Parameter> parameters,
    String returnType,
    List<String> statements) {

  private static final Pattern TYPE_NAMES = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

  /**
   * An annotation.
   *
   * @param name the annotation name, without {@code @}
   * @param arguments the arguments, as source text
   */
  public record Decorator(String name, List<String> arguments) {
    /** Makes the arguments immutable. */
    public Decorator {
      arguments = List.copyOf(arguments);
    }
  }

  /**
   * A parameter.
   *
   * @param name the parameter name
   * @param type the parameter type
   * @param hasQuestionToken whether the value may be absent; Java has no optional parameters, so Booster passes
   *     {@code null} (for example the current state of an entity before its first event)
   */
  public record Parameter(String name, String type, boolean hasQuestionToken) {}

  /** Makes the collections immutable. */
  public MethodDeclaration {
    decorators = List.copyOf(decorators);
    parameters = List.copyOf(parameters);
    statements = List.copyOf(statements);
  }

  /**
   * Renders the method.
   *
   * @param indent the indentation of the member (for example two spaces)
   * @param unit one indentation level, used for the body
   * @return the source text, without a trailing newline
   */
  public String render(String indent, String unit) {
    List<String> lines = new ArrayList<>();
    for (Decorator decorator : decorators) {
      String args = decorator.arguments().isEmpty() ? "" : "(" + String.join(", ", decorator.arguments()) + ")";
      lines.add(indent + "@" + decorator.name() + args);
    }
    String params = parameters.stream().map(p -> p.type() + " " + p.name()).collect(Collectors.joining(", "));
    String modifiers = (scope == null || scope.isEmpty() ? "" : scope + " ") + (isStatic ? "static " : "");
    lines.add(indent + modifiers + returnType + " " + name + "(" + params + ") {");
    for (String statement : statements) {
      String trimmed = statement.strip();
      boolean needsSemicolon = !trimmed.endsWith(";") && !trimmed.endsWith("}") && !trimmed.endsWith("{");
      lines.add(indent + unit + trimmed + (needsSemicolon ? ";" : ""));
    }
    lines.add(indent + "}");
    return String.join("\n", lines);
  }

  /**
   * The simple type names this method refers to (annotations, parameter types and the return type, generic
   * arguments included), used to add the missing imports.
   *
   * @return the referenced names, in order of appearance
   */
  public Set<String> referencedTypeNames() {
    Set<String> names = new LinkedHashSet<>();
    decorators.forEach(d -> names.add(d.name()));
    decorators.forEach(d -> d.arguments().forEach(arg -> classLiterals(arg, names)));
    parameters.forEach(p -> typeNames(p.type(), names));
    typeNames(returnType, names);
    return names;
  }

  private static void typeNames(String type, Set<String> names) {
    Matcher matcher = TYPE_NAMES.matcher(type);
    while (matcher.find()) {
      String name = matcher.group();
      if (!name.contains(".") && !JavaNames.KEYWORDS.contains(name)) {
        names.add(name);
      }
    }
  }

  private static void classLiterals(String argument, Set<String> names) {
    Matcher matcher = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\.class").matcher(argument);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
  }
}
