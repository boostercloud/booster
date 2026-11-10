package com.boostercloud.cli.services.java;

import com.boostercloud.cli.common.CliException;
import java.util.List;

/**
 * A top-level class or record of a {@link JavaSourceFile}, the subset of ts-morph's {@code ClassDeclaration}
 * needed to append methods.
 */
public final class ClassDeclaration {
  private static final String DEFAULT_INDENT = "  ";

  private final JavaSourceFile sourceFile;
  private final String name;

  ClassDeclaration(JavaSourceFile sourceFile, String name) {
    this.sourceFile = sourceFile;
    this.name = name;
  }

  /** @return the simple name of the class */
  public String name() {
    return name;
  }

  /**
   * Appends a method as the last member of the class, separated from the previous member by a blank line. The
   * indentation of the existing members is reused (two spaces by default, as configured in TypeScript).
   *
   * @param method the method
   * @return this class
   */
  public ClassDeclaration addMethod(MethodDeclaration method) {
    String text = sourceFile.text();
    Body body = locateBody(text);
    String closingIndent = lineIndentation(text, body.close());
    String memberIndent = memberIndentation(text, body);
    if (memberIndent.length() <= closingIndent.length() || !memberIndent.startsWith(closingIndent)) {
      memberIndent = closingIndent + DEFAULT_INDENT;
    }
    String unit = memberIndent.substring(closingIndent.length());
    String trimmedBefore = text.substring(0, body.close()).stripTrailing();
    String after = text.substring(body.close());
    String separator = trimmedBefore.endsWith("{") ? "\n" : "\n\n";
    String rendered = method.render(memberIndent, unit);
    sourceFile.replaceText(trimmedBefore + separator + rendered + "\n" + closingIndent + after);
    sourceFile.addReferencedTypes(method.referencedTypeNames());
    return this;
  }

  /**
   * Appends several methods, in order.
   *
   * @param methods the methods
   * @return this class
   */
  public ClassDeclaration addMethods(List<MethodDeclaration> methods) {
    methods.forEach(this::addMethod);
    return this;
  }

  private record Body(int open, int close) {}

  private Body locateBody(String text) {
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(text);
    int depth = 0;
    for (int i = 0; i < tokens.size(); i++) {
      JavaLexer.Token token = tokens.get(i);
      if (token.is("{")) {
        depth++;
      } else if (token.is("}")) {
        depth--;
      } else if (depth == 0
          && (token.is("class") || token.is("record"))
          && i + 1 < tokens.size()
          && tokens.get(i + 1).text().equals(name)
          && (i == 0 || !tokens.get(i - 1).is("."))) {
        int parens = 0;
        for (int j = i + 2; j < tokens.size(); j++) {
          JavaLexer.Token t = tokens.get(j);
          if (t.is("(")) {
            parens++;
          } else if (t.is(")")) {
            parens--;
          } else if (t.is("{") && parens == 0) {
            int braces = 0;
            for (int k = j; k < tokens.size(); k++) {
              if (tokens.get(k).is("{")) {
                braces++;
              } else if (tokens.get(k).is("}")) {
                braces--;
                if (braces == 0) {
                  return new Body(t.start(), tokens.get(k).start());
                }
              }
            }
            throw new CliException("The class '" + name + "' has no closing brace.");
          }
        }
      }
    }
    throw new CliException("Expected to find a class named '" + name + "'.");
  }

  private static String memberIndentation(String text, Body body) {
    String inside = text.substring(body.open() + 1, body.close());
    for (String line : inside.split("\n")) {
      if (!line.isBlank()) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
          i++;
        }
        return line.substring(0, i);
      }
    }
    return "";
  }

  private static String lineIndentation(String text, int offset) {
    int lineStart = text.lastIndexOf('\n', offset - 1) + 1;
    int i = lineStart;
    while (i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
      i++;
    }
    return text.substring(lineStart, i);
  }
}
