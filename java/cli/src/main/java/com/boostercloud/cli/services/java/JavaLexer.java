package com.boostercloud.cli.services.java;

import java.util.ArrayList;
import java.util.List;

/**
 * A small tokenizer for Java source code. It skips whitespace and comments and understands string, text-block and
 * character literals, which is all the CLI needs to locate declarations, braces and literals reliably (the
 * TypeScript CLI uses the full ts-morph compiler API for the same purpose).
 */
public final class JavaLexer {
  /** Token kinds. */
  public enum Kind {
    /** An identifier or keyword. */
    IDENTIFIER,
    /** A numeric literal. */
    NUMBER,
    /** A string literal or text block (the text includes the quotes). */
    STRING,
    /** A character literal. */
    CHAR,
    /** Any other single character ({@code { } ( ) ; . = < > @ ,} ...). */
    SYMBOL
  }

  /**
   * A token.
   *
   * @param kind the kind
   * @param text the exact source text
   * @param start the offset of the first character
   * @param end the offset after the last character
   */
  public record Token(Kind kind, String text, int start, int end) {
    /**
     * Whether this token is the given symbol or identifier.
     *
     * @param value the expected text
     * @return {@code true} when the text matches and the token is not a literal
     */
    public boolean is(String value) {
      return (kind == Kind.SYMBOL || kind == Kind.IDENTIFIER) && text.equals(value);
    }
  }

  private JavaLexer() {}

  /**
   * Tokenizes a compilation unit. Unterminated literals or comments end at the end of the text.
   *
   * @param source the source code
   * @return the tokens, in order
   */
  public static List<Token> tokenize(String source) {
    List<Token> tokens = new ArrayList<>();
    int i = 0;
    int length = source.length();
    while (i < length) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
        while (i < length && source.charAt(i) != '\n') {
          i++;
        }
      } else if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
        int close = source.indexOf("*/", i + 2);
        i = close < 0 ? length : close + 2;
      } else if (c == '"') {
        int start = i;
        if (source.startsWith("\"\"\"", i)) {
          i = endOfTextBlock(source, i + 3);
        } else {
          i = endOfQuoted(source, i + 1, '"');
        }
        tokens.add(new Token(Kind.STRING, source.substring(start, i), start, i));
      } else if (c == '\'') {
        int start = i;
        i = endOfQuoted(source, i + 1, '\'');
        tokens.add(new Token(Kind.CHAR, source.substring(start, i), start, i));
      } else if (Character.isJavaIdentifierStart(c)) {
        int start = i;
        while (i < length && Character.isJavaIdentifierPart(source.charAt(i))) {
          i++;
        }
        tokens.add(new Token(Kind.IDENTIFIER, source.substring(start, i), start, i));
      } else if (Character.isDigit(c)) {
        int start = i;
        while (i < length && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_' || source.charAt(i) == '.')) {
          i++;
        }
        tokens.add(new Token(Kind.NUMBER, source.substring(start, i), start, i));
      } else {
        tokens.add(new Token(Kind.SYMBOL, String.valueOf(c), i, i + 1));
        i++;
      }
    }
    return tokens;
  }

  /**
   * Decodes the value of a string literal token (escape sequences included). Text blocks are returned with their
   * content stripped of the incidental indentation.
   *
   * @param literal the token text, quotes included
   * @return the string value
   */
  public static String stringValue(String literal) {
    if (literal.startsWith("\"\"\"")) {
      String body = literal.length() >= 6 ? literal.substring(3, literal.length() - 3) : literal.substring(3);
      int firstNewline = body.indexOf('\n');
      body = firstNewline >= 0 ? body.substring(firstNewline + 1) : body;
      return unescape(body.stripIndent());
    }
    String body = literal.length() >= 2 && literal.endsWith("\"") ? literal.substring(1, literal.length() - 1) : literal.substring(1);
    return unescape(body);
  }

  private static String unescape(String text) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '\\' || i + 1 >= text.length()) {
        sb.append(c);
        continue;
      }
      char next = text.charAt(++i);
      switch (next) {
        case 'n' -> sb.append('\n');
        case 't' -> sb.append('\t');
        case 'r' -> sb.append('\r');
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 's' -> sb.append(' ');
        case '0', '1', '2', '3', '4', '5', '6', '7' -> {
          int end = i;
          while (end < text.length() && end < i + 3 && text.charAt(end) >= '0' && text.charAt(end) <= '7') {
            end++;
          }
          sb.append((char) Integer.parseInt(text.substring(i, end), 8));
          i = end - 1;
        }
        case 'u' -> {
          int j = i;
          while (j < text.length() && text.charAt(j) == 'u') {
            j++;
          }
          if (j + 4 <= text.length()) {
            try {
              sb.append((char) Integer.parseInt(text.substring(j, j + 4), 16));
              i = j + 3;
            } catch (NumberFormatException e) {
              sb.append('\\').append(next);
            }
          } else {
            sb.append('\\').append(next);
          }
        }
        case '\n' -> {
          // line continuation in text blocks
        }
        default -> sb.append(next);
      }
    }
    return sb.toString();
  }

  private static int endOfQuoted(String source, int from, char quote) {
    int i = from;
    while (i < source.length()) {
      char c = source.charAt(i);
      if (c == '\\') {
        i += 2;
      } else if (c == quote) {
        return i + 1;
      } else if (c == '\n') {
        return i;
      } else {
        i++;
      }
    }
    return source.length();
  }

  private static int endOfTextBlock(String source, int from) {
    int i = from;
    while (i < source.length()) {
      char c = source.charAt(i);
      if (c == '\\') {
        i += 2;
      } else if (source.startsWith("\"\"\"", i)) {
        return i + 3;
      } else {
        i++;
      }
    }
    return source.length();
  }
}
