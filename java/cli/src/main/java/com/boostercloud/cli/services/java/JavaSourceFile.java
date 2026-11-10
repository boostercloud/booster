package com.boostercloud.cli.services.java;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.FrameworkApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An editable Java compilation unit, the small subset of ts-morph's {@code SourceFile} used by the {@code add:*}
 * commands: find a class, add methods to it, add the imports those methods need and save.
 */
public final class JavaSourceFile {
  private static final Set<String> TYPE_KEYWORDS = Set.of("class", "record", "interface", "enum");

  private final Path path;
  private String text;
  private final Set<String> referencedTypes = new LinkedHashSet<>();

  JavaSourceFile(Path path, String text) {
    this.path = path;
    this.text = text;
  }

  /**
   * Reads a source file.
   *
   * @param path the file
   * @return the source file
   * @throws CliException when the file cannot be read
   */
  public static JavaSourceFile read(Path path) {
    try {
      return new JavaSourceFile(path, Files.readString(path, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new CliException("Could not read the source file " + path + ": " + e.getMessage(), e);
    }
  }

  /**
   * Creates an in-memory source file (it can still be saved to {@code path}).
   *
   * @param path where the file would be saved
   * @param text the source code
   * @return the source file
   */
  public static JavaSourceFile of(Path path, String text) {
    return new JavaSourceFile(path, text);
  }

  /** @return the location of the file */
  public Path path() {
    return path;
  }

  /** @return the current source code, including unsaved changes */
  public String text() {
    return text;
  }

  /** @return the package of the compilation unit ({@code ""} for the default package) */
  public String packageName() {
    return ProjectIndex.packageOf(text);
  }

  /**
   * The single-type and on-demand imports of the compilation unit, without {@code import} and {@code ;}.
   *
   * @return for example {@code [java.util.List, com.example.events.*]}
   */
  public List<String> imports() {
    List<String> imports = new ArrayList<>();
    for (ImportLine line : importLines()) {
      if (!line.isStatic()) {
        imports.add(line.name());
      }
    }
    return imports;
  }

  /**
   * Finds a top-level class or record declared in this file.
   *
   * @param name the simple name of the class
   * @return the declaration
   * @throws CliException {@code Expected to find a class named '<name>'.} when there is none
   */
  public ClassDeclaration getClassOrThrow(String name) {
    return findClass(name).orElseThrow(() -> new CliException("Expected to find a class named '" + name + "'."));
  }

  /**
   * Finds a top-level class or record declared in this file.
   *
   * @param name the simple name of the class
   * @return the declaration, if found
   */
  public Optional<ClassDeclaration> findClass(String name) {
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(text);
    int depth = 0;
    for (int i = 0; i < tokens.size(); i++) {
      JavaLexer.Token token = tokens.get(i);
      if (token.is("{")) {
        depth++;
      } else if (token.is("}")) {
        depth--;
      } else if (depth == 0
          && token.kind() == JavaLexer.Kind.IDENTIFIER
          && (token.text().equals("class") || token.text().equals("record"))
          && i + 1 < tokens.size()
          && tokens.get(i + 1).text().equals(name)
          && (i == 0 || !tokens.get(i - 1).is("."))) {
        return Optional.of(new ClassDeclaration(this, name));
      }
    }
    return Optional.empty();
  }

  /**
   * Adds the imports needed by the methods added since the file was read, the counterpart of ts-morph's
   * {@code fixMissingImports}. A referenced type is imported when it is not already visible (same package,
   * declared in this file, {@code java.lang}, or already imported) and its qualified name is known: framework types
   * from {@link FrameworkApi}, project types from the index when exactly one class has that name.
   *
   * @param index the project's types
   * @return this source file
   */
  public JavaSourceFile fixMissingImports(ProjectIndex index) {
    String packageName = packageName();
    List<String> existing = imports();
    Set<String> declared = declaredTypeNames();
    List<String> toAdd = new ArrayList<>();
    for (String simpleName : referencedTypes) {
      if (declared.contains(simpleName) || isJavaLang(simpleName) || JavaNames.KEYWORDS.contains(simpleName)) {
        continue;
      }
      if (existing.stream().anyMatch(i -> JavaNames.simpleNameOf(i).equals(simpleName))) {
        continue;
      }
      Optional<String> qualified = FrameworkApi.qualifiedName(simpleName).or(() -> index.uniqueQualifiedName(simpleName));
      if (qualified.isEmpty()) {
        continue;
      }
      String qualifiedName = qualified.get();
      String typePackage = JavaNames.packageOf(qualifiedName);
      if (typePackage.equals(packageName) || existing.contains(typePackage + ".*") || toAdd.contains(qualifiedName)) {
        continue;
      }
      toAdd.add(qualifiedName);
    }
    toAdd.sort(String::compareTo);
    for (String qualifiedName : toAdd) {
      addImport(qualifiedName);
    }
    referencedTypes.clear();
    return this;
  }

  /**
   * Writes the source code back to its file.
   *
   * @throws CliException when the file cannot be written
   */
  public void save() {
    try {
      Files.writeString(path, text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CliException("Could not write the source file " + path + ": " + e.getMessage(), e);
    }
  }

  /**
   * Adds a single-type import, keeping a sorted import block sorted.
   *
   * @param qualifiedName the type to import
   */
  public void addImport(String qualifiedName) {
    List<ImportLine> lines = importLines();
    String newLine = "import " + qualifiedName + ";";
    if (lines.isEmpty()) {
      int insertAt = packageDeclarationEnd();
      if (insertAt < 0) {
        text = newLine + "\n\n" + text;
      } else {
        String before = text.substring(0, insertAt);
        String after = text.substring(insertAt).replaceFirst("^[ \\t]*\\r?\\n?", "");
        text = before + "\n\n" + newLine + "\n" + (after.startsWith("\n") ? "" : "\n") + after;
      }
      return;
    }
    ImportLine target = null;
    for (ImportLine line : lines) {
      if (!line.isStatic() && line.name().compareTo(qualifiedName) > 0) {
        target = line;
        break;
      }
    }
    if (target != null) {
      int lineStart = text.lastIndexOf('\n', target.start() - 1) + 1;
      text = text.substring(0, lineStart) + newLine + "\n" + text.substring(lineStart);
    } else {
      ImportLine last = lines.getLast();
      text = text.substring(0, last.end()) + "\n" + newLine + text.substring(last.end());
    }
  }

  void replaceText(String newText) {
    this.text = newText;
  }

  void addReferencedTypes(Set<String> names) {
    referencedTypes.addAll(names);
  }

  private Set<String> declaredTypeNames() {
    Set<String> names = new LinkedHashSet<>();
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(text);
    for (int i = 0; i + 1 < tokens.size(); i++) {
      JavaLexer.Token token = tokens.get(i);
      if (token.kind() == JavaLexer.Kind.IDENTIFIER
          && TYPE_KEYWORDS.contains(token.text())
          && tokens.get(i + 1).kind() == JavaLexer.Kind.IDENTIFIER
          && (i == 0 || !tokens.get(i - 1).is("."))) {
        names.add(tokens.get(i + 1).text());
      }
    }
    return names;
  }

  private static boolean isJavaLang(String simpleName) {
    try {
      Class.forName("java.lang." + simpleName, false, JavaSourceFile.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  private int packageDeclarationEnd() {
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(text);
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).is("package")) {
        for (int j = i + 1; j < tokens.size(); j++) {
          if (tokens.get(j).is(";")) {
            return tokens.get(j).end();
          }
        }
      }
      if (tokens.get(i).is("import") || TYPE_KEYWORDS.contains(tokens.get(i).text())) {
        return -1;
      }
    }
    return -1;
  }

  private record ImportLine(String name, boolean isStatic, int start, int end) {}

  private List<ImportLine> importLines() {
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(text);
    List<ImportLine> lines = new ArrayList<>();
    int depth = 0;
    for (int i = 0; i < tokens.size(); i++) {
      JavaLexer.Token token = tokens.get(i);
      if (token.is("{")) {
        depth++;
      } else if (token.is("}")) {
        depth--;
      } else if (depth == 0 && token.is("import")) {
        int j = i + 1;
        boolean isStatic = j < tokens.size() && tokens.get(j).is("static");
        if (isStatic) {
          j++;
        }
        StringBuilder name = new StringBuilder();
        while (j < tokens.size() && !tokens.get(j).is(";")) {
          name.append(tokens.get(j).text());
          j++;
        }
        int end = j < tokens.size() ? tokens.get(j).end() : text.length();
        lines.add(new ImportLine(name.toString(), isStatic, token.start(), end));
        i = j;
      } else if (depth == 0 && TYPE_KEYWORDS.contains(token.text()) && token.kind() == JavaLexer.Kind.IDENTIFIER) {
        break;
      }
    }
    return lines;
  }
}
