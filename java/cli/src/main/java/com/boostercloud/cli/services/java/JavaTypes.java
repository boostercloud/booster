package com.boostercloud.cli.services.java;

import com.boostercloud.cli.services.FrameworkApi;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the TypeScript-style field types accepted by {@code --fields name:type} to Java types.
 *
 * <ul>
 *   <li>{@code string} → {@code String}, {@code number} → {@code double}, {@code boolean} → {@code boolean}
 *       (boxed inside generic arguments), {@code any}/{@code unknown}/{@code object} → {@code Object};
 *   <li>{@code UUID} → Booster's {@code UUID}, {@code Date} → {@code java.time.Instant};
 *   <li>{@code Array<T>}, {@code ReadonlyArray<T>} and {@code T[]} → {@code List<T>}, {@code Record<K, V>} →
 *       {@code Map<K, V>};
 *   <li>Java primitives and well-known JDK types ({@code List}, {@code Set}, {@code Map}, {@code Optional},
 *       {@code Instant}, {@code BigDecimal}...) are kept and imported;
 *   <li>any other identifier is kept as-is and reported in {@link JavaType#unresolvedSimpleNames()} so that it can
 *       be imported when it names a class of the project.
 * </ul>
 */
public final class JavaTypes {
  private static final Map<String, String> PRIMITIVE_BOXES =
      Map.of(
          "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte", "char", "Character", "float", "Float",
          "double", "Double", "boolean", "Boolean");

  private static final Set<String> JAVA_LANG =
      Set.of(
          "String", "Object", "Integer", "Long", "Short", "Byte", "Character", "Float", "Double", "Boolean", "Number",
          "Void", "CharSequence", "Iterable", "Comparable", "Enum", "Record", "Class");

  private static final Map<String, String> JDK_TYPES =
      Map.ofEntries(
          Map.entry("List", "java.util.List"),
          Map.entry("Set", "java.util.Set"),
          Map.entry("Map", "java.util.Map"),
          Map.entry("Optional", "java.util.Optional"),
          Map.entry("Collection", "java.util.Collection"),
          Map.entry("SortedSet", "java.util.SortedSet"),
          Map.entry("SortedMap", "java.util.SortedMap"),
          Map.entry("Instant", "java.time.Instant"),
          Map.entry("LocalDate", "java.time.LocalDate"),
          Map.entry("LocalDateTime", "java.time.LocalDateTime"),
          Map.entry("LocalTime", "java.time.LocalTime"),
          Map.entry("OffsetDateTime", "java.time.OffsetDateTime"),
          Map.entry("ZonedDateTime", "java.time.ZonedDateTime"),
          Map.entry("Duration", "java.time.Duration"),
          Map.entry("Period", "java.time.Period"),
          Map.entry("BigDecimal", "java.math.BigDecimal"),
          Map.entry("BigInteger", "java.math.BigInteger"));

  /**
   * A mapped type.
   *
   * @param rendered the Java source text of the type
   * @param imports the fully qualified names to import
   * @param unresolvedSimpleNames simple names that are neither primitives, JDK types nor framework types
   */
  public record JavaType(String rendered, Set<String> imports, Set<String> unresolvedSimpleNames) {
    /** Makes the collections immutable. */
    public JavaType {
      imports = Set.copyOf(imports);
      unresolvedSimpleNames = Set.copyOf(unresolvedSimpleNames);
    }
  }

  private JavaTypes() {}

  /**
   * Maps a field type. When the text cannot be parsed as a type expression it is returned unchanged.
   *
   * @param type the type as written in {@code --fields}
   * @return the Java type
   */
  public static JavaType toJava(String type) {
    try {
      Parser parser = new Parser(type);
      Node node = parser.parseType();
      parser.expectEnd();
      Set<String> imports = new LinkedHashSet<>();
      Set<String> unresolved = new LinkedHashSet<>();
      String rendered = render(node, false, imports, unresolved);
      return new JavaType(rendered, imports, unresolved);
    } catch (IllegalArgumentException e) {
      return new JavaType(type, Set.of(), Set.of());
    }
  }

  private static String render(Node node, boolean boxed, Set<String> imports, Set<String> unresolved) {
    if (node.arrayDimensions > 0) {
      Node element = new Node(node.name, node.arguments, node.arrayDimensions - 1);
      imports.add("java.util.List");
      return "List<" + render(element, true, imports, unresolved) + ">";
    }
    String name = node.name;
    List<Node> args = node.arguments;
    switch (name) {
      case "string" -> {
        return "String";
      }
      case "number" -> {
        return boxed ? "Double" : "double";
      }
      case "any", "unknown", "object" -> {
        return "Object";
      }
      case "Date" -> {
        imports.add("java.time.Instant");
        return "Instant";
      }
      case "UUID" -> {
        imports.add(FrameworkApi.require("UUID"));
        return "UUID";
      }
      case "Array", "ReadonlyArray" -> {
        if (args.size() == 1) {
          imports.add("java.util.List");
          return "List<" + render(args.getFirst(), true, imports, unresolved) + ">";
        }
      }
      case "Record" -> {
        if (args.size() == 2) {
          imports.add("java.util.Map");
          return "Map<"
              + render(args.get(0), true, imports, unresolved)
              + ", "
              + render(args.get(1), true, imports, unresolved)
              + ">";
        }
      }
      default -> {
        // handled below
      }
    }
    if (PRIMITIVE_BOXES.containsKey(name)) {
      return boxed ? PRIMITIVE_BOXES.get(name) : name;
    }
    if (JDK_TYPES.containsKey(name)) {
      imports.add(JDK_TYPES.get(name));
    } else if (!name.contains(".") && !JAVA_LANG.contains(name)) {
      var framework = FrameworkApi.qualifiedName(name);
      if (framework.isPresent()) {
        imports.add(framework.get());
      } else {
        unresolved.add(name);
      }
    }
    if (args.isEmpty()) {
      return name;
    }
    List<String> renderedArgs = new ArrayList<>();
    for (Node arg : args) {
      renderedArgs.add(render(arg, true, imports, unresolved));
    }
    return name + "<" + String.join(", ", renderedArgs) + ">";
  }

  private record Node(String name, List<Node> arguments, int arrayDimensions) {}

  private static final class Parser {
    private final String text;
    private int pos;

    Parser(String text) {
      this.text = text;
    }

    Node parseType() {
      skipSpaces();
      String name = parseQualifiedName();
      List<Node> args = new ArrayList<>();
      skipSpaces();
      if (peek('<')) {
        pos++;
        args.add(parseType());
        skipSpaces();
        while (peek(',')) {
          pos++;
          args.add(parseType());
          skipSpaces();
        }
        expect('>');
      }
      int dimensions = 0;
      skipSpaces();
      while (peek('[')) {
        pos++;
        skipSpaces();
        expect(']');
        dimensions++;
        skipSpaces();
      }
      return new Node(name, List.copyOf(args), dimensions);
    }

    void expectEnd() {
      skipSpaces();
      if (pos != text.length()) {
        throw new IllegalArgumentException("Unexpected text in type: " + text);
      }
    }

    private String parseQualifiedName() {
      StringBuilder sb = new StringBuilder(parseIdentifier());
      while (peek('.')) {
        pos++;
        sb.append('.').append(parseIdentifier());
      }
      return sb.toString();
    }

    private String parseIdentifier() {
      int start = pos;
      if (pos >= text.length() || !Character.isJavaIdentifierStart(text.charAt(pos))) {
        throw new IllegalArgumentException("Expected an identifier in type: " + text);
      }
      while (pos < text.length() && Character.isJavaIdentifierPart(text.charAt(pos))) {
        pos++;
      }
      return text.substring(start, pos);
    }

    private boolean peek(char c) {
      return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
      if (!peek(c)) {
        throw new IllegalArgumentException("Expected '" + c + "' in type: " + text);
      }
      pos++;
    }

    private void skipSpaces() {
      while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
        pos++;
      }
    }
  }
}
