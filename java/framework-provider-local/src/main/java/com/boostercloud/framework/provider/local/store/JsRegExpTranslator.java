package com.boostercloud.framework.provider.local.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates a JavaScript regular expression (ECMAScript with the Annex B web-compatibility grammar, the one
 * {@code new RegExp(source, flags)} uses without the {@code u} flag) into an equivalent {@link Pattern}.
 *
 * <p>JavaScript and Java share most of the syntax, but they differ in ways a Booster filter can hit: a lone
 * {@code {}, {@code }} or {@code ]} is a literal in JavaScript and an error (or a quantifier) in Java; {@code \s}
 * includes the Unicode spaces (no-break space, ideographic space...); {@code \v} is the vertical tab (Java: any
 * vertical whitespace); {@code $} without the {@code m} flag only matches at the very end of the input (Java also
 * before a final line terminator); {@code .} excludes only {@code \n}, {@code \r}, U+2028 and U+2029 (Java also
 * U+0085); unknown escapes such as {@code \e}, {@code \a}, {@code \q}, {@code \x} without hex digits or {@code \c}
 * without a letter are identity escapes (Java: other meanings or errors); legacy octal escapes ({@code \012});
 * {@code []} never matches and {@code [^]} matches anything; {@code [} and {@code &&} are literals inside a class;
 * {@code \b} inside a class is a backspace; Java-only syntax ({@code a*+}, {@code (?i)}, {@code \Q...\E},
 * {@code \p{L}} without {@code u}) is an error or a literal in JavaScript. The translation emits explicit
 * constructs for all of these, so that matching is JavaScript's.
 *
 * <p>Syntax errors are reported with V8's message ({@code Invalid regular expression: /(/: Unterminated group}).
 *
 * <p>Not emulated (documented differences): JavaScript's non-Unicode case folding for the few characters where it
 * differs from Java's ({@code ſ} and the Kelvin sign {@code K} do not match {@code s}/{@code k} in JavaScript), and
 * code-unit versus code-point matching of characters outside the BMP ({@code .} or a class consume a whole surrogate
 * pair in Java, one code unit in JavaScript); a back reference to a group that did not participate fails in Java and
 * matches the empty string in JavaScript.
 */
final class JsRegExpTranslator {

  /** JavaScript's {@code \s}: WhiteSpace and LineTerminator. */
  static final String JS_WHITESPACE =
      "\\t\\n\\x0B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";

  private static final String LINE_TERMINATORS = "\\n\\r\\u2028\\u2029";
  private static final Pattern BRACED_QUANTIFIER = Pattern.compile("\\{(\\d+)(,(\\d*))?}");
  private static final Pattern GROUP_NAME = Pattern.compile("[$_\\p{L}\\p{Nl}][$_\\p{L}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\u200C\\u200D]*");

  /** The result of a translation. */
  record Translation(String pattern, int flags) {}

  /** A syntax error with V8's message. */
  static final class Failure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int index;

    Failure(String message, int index) {
      super(message);
      this.index = index;
    }
  }

  private enum Last { NOTHING, QUANTIFIABLE, NOT_QUANTIFIABLE, LOOKAROUND, QUANTIFIED }

  private enum GroupKind { CAPTURING, NON_CAPTURING, LOOKAHEAD, LOOKBEHIND }

  private final String source;
  private final boolean ignoreCase;
  private final boolean multiline;
  private final boolean dotAll;
  private final boolean unicode;
  private final StringBuilder out = new StringBuilder();
  private final Deque<GroupKind> groups = new ArrayDeque<>();
  private final Map<String, String> groupNames = new LinkedHashMap<>();
  private final int capturingGroups;
  private int position;
  private Last last = Last.NOTHING;

  private JsRegExpTranslator(String source, String flags) {
    this.source = source;
    this.ignoreCase = flags.indexOf('i') >= 0;
    this.multiline = flags.indexOf('m') >= 0;
    this.dotAll = flags.indexOf('s') >= 0;
    this.unicode = flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0;
    this.capturingGroups = countCapturingGroups();
  }

  /**
   * @param source the JavaScript pattern source
   * @param flags the validated JavaScript flags
   * @return the Java pattern and flags
   * @throws Failure on a JavaScript syntax error
   */
  static Translation translate(String source, String flags) {
    JsRegExpTranslator translator = new JsRegExpTranslator(source, flags);
    translator.disjunction();
    int javaFlags = translator.ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
    return new Translation(translator.out.toString(), javaFlags);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Pre-pass
  // ---------------------------------------------------------------------------------------------------------------

  /** The number of capturing groups of the whole pattern (Annex B decides between back references and octals). */
  private int countCapturingGroups() {
    int count = 0;
    boolean inClass = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '\\') {
        i++;
      } else if (inClass) {
        if (c == ']') {
          inClass = false;
        }
      } else if (c == '[') {
        inClass = true;
      } else if (c == '(') {
        if (i + 1 >= source.length() || source.charAt(i + 1) != '?') {
          count++;
        } else if (source.startsWith("?<", i + 1) && i + 3 < source.length()
            && source.charAt(i + 3) != '=' && source.charAt(i + 3) != '!') {
          count++;
          int end = source.indexOf('>', i + 3);
          if (end > 0) {
            groupNames.putIfAbsent(source.substring(i + 3, end), "g" + (groupNames.size() + 1) + "n");
          }
        }
      }
    }
    return count;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Grammar
  // ---------------------------------------------------------------------------------------------------------------

  private Failure error(String message) {
    return new Failure(message, position);
  }

  private boolean atEnd() {
    return position >= source.length();
  }

  private char peek() {
    return source.charAt(position);
  }

  private void disjunction() {
    while (!atEnd()) {
      char c = peek();
      switch (c) {
        case '|' -> {
          out.append('|');
          position++;
          last = Last.NOTHING;
        }
        case '(' -> openGroup();
        case ')' -> {
          if (groups.isEmpty()) {
            throw error("Unmatched ')'");
          }
          GroupKind kind = groups.pop();
          out.append(')');
          position++;
          last = kind == GroupKind.LOOKBEHIND || (unicode && kind == GroupKind.LOOKAHEAD)
              ? Last.LOOKAROUND : Last.QUANTIFIABLE;
        }
        case '*', '+', '?' -> quantifier(String.valueOf(c), 1);
        case '{' -> {
          Matcher braced = BRACED_QUANTIFIER.matcher(source).region(position, source.length());
          if (braced.lookingAt()) {
            checkBracedQuantifier(braced);
            quantifier(braced.group(), braced.group().length());
          } else if (unicode) {
            throw error("Lone quantifier brackets");
          } else {
            literal("\\{");
          }
        }
        case '}', ']' -> {
          if (unicode) {
            throw error("Lone quantifier brackets");
          }
          literal("\\" + c);
        }
        case '^' -> {
          out.append(multiline ? "(?:^|(?<=[" + LINE_TERMINATORS + "]))" : "^");
          position++;
          last = Last.NOT_QUANTIFIABLE;
        }
        case '$' -> {
          out.append(multiline ? "(?=[" + LINE_TERMINATORS + "]|\\z)" : "\\z");
          position++;
          last = Last.NOT_QUANTIFIABLE;
        }
        case '.' -> {
          out.append(dotAll ? "[\\s\\S]" : "[^" + LINE_TERMINATORS + "]");
          position++;
          last = Last.QUANTIFIABLE;
        }
        case '[' -> characterClass();
        case '\\' -> atomEscape();
        default -> {
          position++;
          appendLiteralChar(c);
          last = Last.QUANTIFIABLE;
        }
      }
    }
    if (!groups.isEmpty()) {
      throw error("Unterminated group");
    }
  }

  private void literal(String javaText) {
    out.append(javaText);
    position++;
    last = Last.QUANTIFIABLE;
  }

  private void appendLiteralChar(char c) {
    if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
      out.append('\\');
    }
    out.append(c);
  }

  private void checkBracedQuantifier(Matcher braced) {
    if (braced.group(2) != null && !braced.group(3).isEmpty()
        && new java.math.BigInteger(braced.group(1)).compareTo(new java.math.BigInteger(braced.group(3))) > 0) {
      throw error("numbers out of order in {} quantifier");
    }
  }

  private void quantifier(String text, int length) {
    if (last == Last.LOOKAROUND) {
      throw error("Invalid quantifier");
    }
    if (last == Last.NOTHING || last == Last.NOT_QUANTIFIABLE || last == Last.QUANTIFIED) {
      throw error("Nothing to repeat");
    }
    out.append(clampQuantifier(text));
    position += length;
    if (!atEnd() && peek() == '?') {
      out.append('?');
      position++;
    }
    last = Last.QUANTIFIED;
  }

  /** Java rejects bounds above {@link Integer#MAX_VALUE}; JavaScript clamps them. */
  private static String clampQuantifier(String text) {
    if (!text.startsWith("{")) {
      return text;
    }
    Matcher braced = BRACED_QUANTIFIER.matcher(text);
    braced.matches();
    String min = clamp(braced.group(1));
    if (braced.group(2) == null) {
      return "{" + min + "}";
    }
    return "{" + min + "," + (braced.group(3).isEmpty() ? "" : clamp(braced.group(3))) + "}";
  }

  private static String clamp(String number) {
    return new java.math.BigInteger(number).compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE - 1)) > 0
        ? Integer.toString(Integer.MAX_VALUE - 1) : number;
  }

  private void openGroup() {
    position++;
    if (atEnd() || peek() != '?') {
      groups.push(GroupKind.CAPTURING);
      out.append('(');
      last = Last.NOTHING;
      return;
    }
    if (source.startsWith("?:", position)) {
      groups.push(GroupKind.NON_CAPTURING);
      out.append("(?:");
      position += 2;
    } else if (source.startsWith("?=", position) || source.startsWith("?!", position)) {
      groups.push(GroupKind.LOOKAHEAD);
      out.append('(').append(source, position, position + 2);
      position += 2;
    } else if (source.startsWith("?<=", position) || source.startsWith("?<!", position)) {
      groups.push(GroupKind.LOOKBEHIND);
      out.append('(').append(source, position, position + 3);
      position += 3;
    } else if (source.startsWith("?<", position)) {
      int end = source.indexOf('>', position + 2);
      String name = end < 0 ? "" : source.substring(position + 2, end);
      if (end < 0 || !GROUP_NAME.matcher(name).matches()) {
        throw error("Invalid capture group name");
      }
      groups.push(GroupKind.CAPTURING);
      out.append("(?<").append(groupNames.get(name)).append('>');
      position = end + 1;
    } else {
      throw error("Invalid group");
    }
    last = Last.NOTHING;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Escapes
  // ---------------------------------------------------------------------------------------------------------------

  private void atomEscape() {
    position++;
    if (atEnd()) {
      throw error("\\ at end of pattern");
    }
    char c = peek();
    position++;
    last = Last.QUANTIFIABLE;
    switch (c) {
      case 'b', 'B' -> {
        out.append('\\').append(c);
        last = Last.NOT_QUANTIFIABLE;
      }
      case 'd', 'D', 'w', 'W' -> out.append('\\').append(c);
      case 'p', 'P' -> {
        if (unicode) {
          out.append(unicodeProperty(c));
        } else {
          out.append(c);
        }
      }
      case 's' -> out.append('[').append(JS_WHITESPACE).append(']');
      case 'S' -> out.append("[^").append(JS_WHITESPACE).append(']');
      case 'k' -> {
        if (groupNames.isEmpty() && !unicode) {
          out.append('k');
        } else {
          int end = source.startsWith("<", position) ? source.indexOf('>', position) : -1;
          if (end < 0) {
            throw error("Invalid named reference");
          }
          String name = source.substring(position + 1, end);
          if (!groupNames.containsKey(name)) {
            throw error("Invalid named capture referenced");
          }
          out.append("\\k<").append(groupNames.get(name)).append('>');
          position = end + 1;
        }
      }
      case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
        int start = position - 1;
        int end = position;
        while (end < source.length() && Character.isDigit(source.charAt(end))) {
          end++;
        }
        String digits = source.substring(start, end);
        if (digits.length() <= 9 && Integer.parseInt(digits) <= capturingGroups) {
          // a back reference; the group is wrapped so that following digits stay literals
          out.append("(?:\\").append(digits).append(')');
          position = end;
        } else if (unicode) {
          throw error("Invalid escape");
        } else if (c >= '8') {
          out.append(c);
        } else {
          position = start;
          appendCodeUnit(legacyOctal());
        }
      }
      default -> {
        position--;
        appendCodeUnit(characterEscape(false));
      }
    }
  }

  /** {@code \\p{...}} / {@code \\P{...}} of the {@code u} flag (the letter already consumed), in Java syntax. */
  private String unicodeProperty(char letter) {
    int end = source.startsWith("{", position) ? source.indexOf('}', position) : -1;
    if (end < 0) {
      throw error("Invalid property name");
    }
    String property = source.substring(position + 1, end);
    position = end + 1;
    String javaProperty = property.startsWith("General_Category=") ? "gc=" + property.substring(17)
        : property.startsWith("Script=") ? "sc=" + property.substring(7)
        : property;
    return "\\" + letter + "{" + javaProperty + "}";
  }

  /** Reads a CharacterEscape (the backslash already consumed) and returns its code unit. */
  private int characterEscape(boolean inClass) {
    char c = peek();
    position++;
    switch (c) {
      case 'f':
        return '\f';
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 't':
        return '\t';
      case 'v':
        return 0x0B;
      case 'c':
        if (!atEnd() && (isAsciiLetter(peek()) || (inClass && !unicode && (Character.isDigit(peek()) || peek() == '_')))) {
          return source.charAt(position++) % 32;
        }
        if (unicode) {
          throw error("Invalid unicode escape");
        }
        // Annex B: the backslash is a literal and "c" is read again as a pattern character
        position--;
        return '\\';
      case '0':
        if (atEnd() || !Character.isDigit(peek())) {
          return 0;
        }
        if (unicode) {
          throw error("Invalid decimal escape");
        }
        position--;
        return legacyOctal();
      case 'x':
        if (position + 2 <= source.length() && isHex(source.substring(position, position + 2))) {
          int value = Integer.parseInt(source.substring(position, position + 2), 16);
          position += 2;
          return value;
        }
        if (unicode) {
          throw error("Invalid escape");
        }
        return 'x';
      case 'u':
        if (position + 4 <= source.length() && isHex(source.substring(position, position + 4))) {
          int value = Integer.parseInt(source.substring(position, position + 4), 16);
          position += 4;
          return value;
        }
        if (unicode && !atEnd() && peek() == '{') {
          int end = source.indexOf('}', position);
          if (end > position + 1 && isHex(source.substring(position + 1, end))) {
            int codePoint = Integer.parseInt(source.substring(position + 1, end), 16);
            if (codePoint <= 0x10FFFF) {
              position = end + 1;
              return codePoint;
            }
          }
        }
        if (unicode) {
          throw error("Invalid Unicode escape");
        }
        return 'u';
      default:
        if (unicode && "^$\\.*+?()[]{}|/".indexOf(c) < 0 && !(inClass && c == '-')) {
          throw error("Invalid escape");
        }
        // identity escape: the character itself
        return c;
    }
  }

  /** Annex B LegacyOctalEscapeSequence, from the current position (the first octal digit). */
  private int legacyOctal() {
    int value = 0;
    int digits = 0;
    int maxDigits = source.charAt(position) <= '3' ? 3 : 2;
    while (digits < maxDigits && !atEnd() && peek() >= '0' && peek() <= '7') {
      value = value * 8 + (peek() - '0');
      position++;
      digits++;
    }
    return value;
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static boolean isHex(String text) {
    if (text.isEmpty()) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      if (Character.digit(text.charAt(i), 16) < 0) {
        return false;
      }
    }
    return true;
  }

  private void appendCodeUnit(int value) {
    out.append(codeUnit(value));
  }

  /** A code unit (or code point) as a Java escape, safe both outside and inside a character class. */
  private static String codeUnit(int value) {
    if (value > 0xFFFF) {
      return "\\x{" + Integer.toHexString(value) + "}";
    }
    if (value < 0x80 && (Character.isLetterOrDigit(value) || value == '_' || value == ' ')) {
      return String.valueOf((char) value);
    }
    return String.format("\\u%04X", value);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Character classes
  // ---------------------------------------------------------------------------------------------------------------

  /** One element of a class: a single code unit, or a class escape that cannot be a range end. */
  private record ClassAtom(int value, String classEscape) {
    boolean isRangeable() {
      return classEscape == null;
    }
  }

  private void characterClass() {
    int start = position;
    position++;
    boolean negated = !atEnd() && peek() == '^';
    if (negated) {
      position++;
    }
    if (!atEnd() && peek() == ']') {
      position++;
      // [] never matches, [^] matches any code unit
      out.append(negated ? "[\\s\\S]" : "[^\\s\\S]");
      last = Last.QUANTIFIABLE;
      return;
    }
    StringBuilder body = new StringBuilder();
    while (true) {
      if (atEnd()) {
        position = start;
        throw error("Unterminated character class");
      }
      if (peek() == ']') {
        position++;
        break;
      }
      ClassAtom first = classAtom();
      if (!atEnd() && peek() == '-' && position + 1 < source.length() && source.charAt(position + 1) != ']') {
        int dash = position;
        position++;
        ClassAtom second = classAtom();
        if (first.isRangeable() && second.isRangeable()) {
          if (first.value() > second.value()) {
            throw error("Range out of order in character class");
          }
          body.append(codeUnit(first.value())).append('-').append(codeUnit(second.value()));
          continue;
        }
        if (unicode) {
          position = dash;
          throw error("Invalid character class");
        }
        // Annex B: a class escape next to "-" makes the dash a literal
        appendClassAtom(body, first);
        body.append("\\-");
        appendClassAtom(body, second);
        continue;
      }
      appendClassAtom(body, first);
    }
    out.append(negated ? "[^" : "[").append(body).append(']');
    last = Last.QUANTIFIABLE;
  }

  private static void appendClassAtom(StringBuilder body, ClassAtom atom) {
    body.append(atom.isRangeable() ? codeUnit(atom.value()) : atom.classEscape());
  }

  private ClassAtom classAtom() {
    char c = peek();
    if (c != '\\') {
      position++;
      return new ClassAtom(c, null);
    }
    position++;
    if (atEnd()) {
      throw error("\\ at end of pattern");
    }
    char e = peek();
    switch (e) {
      case 'd', 'D', 'w', 'W' -> {
        position++;
        return new ClassAtom(-1, "\\" + e);
      }
      case 's' -> {
        position++;
        return new ClassAtom(-1, JS_WHITESPACE);
      }
      case 'p', 'P' -> {
        if (!unicode) {
          position++;
          return new ClassAtom(e, null);
        }
        position++;
        return new ClassAtom(-1, unicodeProperty(e));
      }
      case 'S' -> {
        position++;
        return new ClassAtom(-1, "[^" + JS_WHITESPACE + "]");
      }
      case 'b' -> {
        position++;
        return new ClassAtom('\b', null);
      }
      case '-' -> {
        position++;
        return new ClassAtom('-', null);
      }
      case 'k' -> {
        if (unicode) {
          throw error("Invalid escape");
        }
        position++;
        return new ClassAtom('k', null);
      }
      case '1', '2', '3', '4', '5', '6', '7' -> {
        if (unicode) {
          throw error("Invalid class escape");
        }
        return new ClassAtom(legacyOctal(), null);
      }
      case '8', '9' -> {
        if (unicode) {
          throw error("Invalid class escape");
        }
        position++;
        return new ClassAtom(e, null);
      }
      default -> {
        return new ClassAtom(characterEscape(true), null);
      }
    }
  }
}
