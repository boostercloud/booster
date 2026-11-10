package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.provider.local.store.JsRegExp;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the npm package {@code minimatch} 3.1.5 with its default options, the glob matcher behind
 * {@link Utils#isValidDirectory(String, List)} (TypeScript calls {@code minimatch(path, pattern)}).
 *
 * <p>The algorithm is minimatch's, step by step: comments ({@code #...}) match nothing, the pattern is trimmed, leading
 * {@code !}s negate it, braces are expanded ({@link BraceExpansion}), the expansions are split on {@code /} and every
 * part becomes {@code **} (globstar), a literal or a regular expression built with minimatch's translation
 * ({@code *}, {@code ?}, classes, extglobs {@code +(a|b)} / {@code !(a)} ..., {@code \} escapes, no dot files). The
 * regular expressions are JavaScript sources compiled with {@link JsRegExp}, so they behave exactly as V8's (including
 * minimatch's own quirks, such as a class left open at the end of a part). Matching splits the path on {@code /} and
 * keeps minimatch's rules: a trailing empty segment is accepted after the last part ({@code exact/} matches
 * {@code exact}), {@code **} never matches a segment starting with a dot, and the globstar recursion is bounded at 200
 * (a deliberate false negative of 3.1.5). As in minimatch, on Windows {@code \} in the pattern and in the path is
 * treated as {@code /}.
 *
 * <p>Pinned against the JavaScript package by {@code MinimatchDifferentialTest} (thousands of generated cases).
 */
public final class Minimatch {

  /** The longest accepted pattern ({@code MAX_PATTERN_LENGTH}). */
  public static final int MAX_PATTERN_LENGTH = 1024 * 64;

  /** {@code maxGlobstarRecursion}'s default. */
  static final int MAX_GLOBSTAR_RECURSION = 200;

  /** Any single character other than {@code /}. */
  private static final String QMARK = "[^/]";
  /** {@code *}: any number of characters. */
  private static final String STAR = QMARK + "*?";
  /** Characters that need to be escaped in a regular expression. */
  private static final String RE_SPECIALS = "().*{}+?[]^$\\!";
  /** {@code /\{(?:(?!\{).)*\}/}: whether braces need expanding. */
  private static final Pattern BRACE_SET = Pattern.compile("\\{(?:(?!\\{)[^\\n\\r\\u2028\\u2029])*\\}");
  /** {@code /((?:\\{2}){0,64})(\\?)\|/g} of the unclosed extglob handling. */
  private static final Pattern UNESCAPED_PIPE = Pattern.compile("((?:\\\\{2}){0,64})(\\\\?)\\|");
  /** {@code /\)[+*?]?/}. */
  private static final Pattern CLOSING_PAREN = Pattern.compile("\\)[+*?]?");
  /** {@code /\\(.)/g} of {@code globUnescape}. */
  private static final Pattern GLOB_ESCAPE = Pattern.compile("\\\\([^\\n\\r\\u2028\\u2029])");
  private static final JsRegExp NEVER = new JsRegExp("$.");
  private static final char SEPARATOR = File.separatorChar;

  /** One part of a pattern between slashes. */
  private sealed interface Part permits Globstar, Literal, Regex {}

  /** {@code GLOBSTAR}. */
  private enum Globstar implements Part { INSTANCE }

  /** A part without magic: compared as is (after unescaping). */
  private record Literal(String value) implements Part {}

  /** A part with magic. */
  private record Regex(JsRegExp regex) implements Part {}

  /** The {@code [re, hasMagic]} result of a sub-parse. */
  private record SubResult(String re, boolean hasMagic) {}

  /** What a sub-parse returns where JavaScript returns a non-array ({@code sp[0]} is {@code undefined}). */
  private static final SubResult UNDEFINED_SUB = new SubResult("undefined", false);

  private final List<List<Part>> set;
  private final boolean negate;
  private final boolean comment;
  private final boolean empty;

  /**
   * {@code new Minimatch(pattern)}.
   *
   * @param pattern the glob pattern
   * @throws IllegalArgumentException {@code invalid pattern} for {@code null}, {@code pattern is too long} beyond
   *     {@value #MAX_PATTERN_LENGTH} characters
   */
  public Minimatch(String pattern) {
    assertValidPattern(pattern);
    String trimmed = jsTrim(pattern);
    if (SEPARATOR != '/') {
      trimmed = trimmed.replace(String.valueOf(SEPARATOR), "/");
    }
    // make()
    if (!trimmed.isEmpty() && trimmed.charAt(0) == '#') {
      this.comment = true;
      this.empty = false;
      this.negate = false;
      this.set = List.of();
      return;
    }
    this.comment = false;
    if (trimmed.isEmpty()) {
      this.empty = true;
      this.negate = false;
      this.set = List.of();
      return;
    }
    this.empty = false;
    // parseNegate()
    boolean negated = false;
    int negateOffset = 0;
    while (negateOffset < trimmed.length() && trimmed.charAt(negateOffset) == '!') {
      negated = !negated;
      negateOffset++;
    }
    this.negate = negated;
    String body = trimmed.substring(negateOffset);
    List<List<Part>> parts = new ArrayList<>();
    for (String expansion : braceExpand(body)) {
      List<Part> compiled = new ArrayList<>();
      boolean valid = true;
      for (String segment : expansion.split("/+", -1)) {
        Part part = (Part) parse(segment, false);
        if (part == null) {
          valid = false;
          break;
        }
        compiled.add(part);
      }
      if (valid) {
        parts.add(List.copyOf(compiled));
      }
    }
    this.set = List.copyOf(parts);
  }

  /**
   * {@code minimatch(path, pattern)}.
   *
   * @param path the path to test
   * @param pattern the glob pattern
   * @return whether the path matches
   * @throws IllegalArgumentException {@code invalid pattern} / {@code pattern is too long}
   */
  public static boolean minimatch(String path, String pattern) {
    assertValidPattern(pattern);
    if (!pattern.isEmpty() && pattern.charAt(0) == '#') {
      return false;
    }
    return new Minimatch(pattern).match(path);
  }

  /**
   * {@code mm.match(path)}.
   *
   * @param path the path to test
   * @return whether it matches
   */
  public boolean match(String path) {
    if (comment) {
      return false;
    }
    if (empty) {
      return path.isEmpty();
    }
    String f = SEPARATOR != '/' ? path.replace(String.valueOf(SEPARATOR), "/") : path;
    String[] file = f.split("/+", -1);
    for (List<Part> pattern : set) {
      if (matchOne(file, pattern)) {
        return !negate;
      }
    }
    return negate;
  }

  private static void assertValidPattern(String pattern) {
    if (pattern == null) {
      throw new IllegalArgumentException("invalid pattern");
    }
    if (pattern.length() > MAX_PATTERN_LENGTH) {
      throw new IllegalArgumentException("pattern is too long");
    }
  }

  private static List<String> braceExpand(String pattern) {
    assertValidPattern(pattern);
    if (!BRACE_SET.matcher(pattern).find()) {
      return List.of(pattern);
    }
    return BraceExpansion.expand(pattern);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // parse(pattern, isSub): one part of the pattern to GLOBSTAR, a literal, a regular expression, or false (null)
  // ---------------------------------------------------------------------------------------------------------------

  private static Object parse(String pattern, boolean isSub) {
    assertValidPattern(pattern);
    if (pattern.equals("**")) {
      return isSub ? UNDEFINED_SUB : Globstar.INSTANCE;
    }
    if (pattern.isEmpty()) {
      return isSub ? UNDEFINED_SUB : new Literal("");
    }
    return new Parser(pattern, isSub).run();
  }

  /** The state of one {@code parse} call (the closure variables of the JavaScript function). */
  private static final class Parser {
    private final String pattern;
    private final boolean isSub;
    private String re = "";
    private boolean hasMagic;
    private boolean escaping;
    private final List<PatternList> patternListStack = new ArrayList<>();
    private final List<PatternList> negativeLists = new ArrayList<>();
    private char stateChar;
    private boolean inClass;
    private int reClassStart = -1;
    private int classStart = -1;

    private Parser(String pattern, boolean isSub) {
      this.pattern = pattern;
      this.isSub = isSub;
    }

    private void clearStateChar() {
      if (stateChar != 0) {
        switch (stateChar) {
          case '*' -> {
            re += STAR;
            hasMagic = true;
          }
          case '?' -> {
            re += QMARK;
            hasMagic = true;
          }
          default -> re += "\\" + stateChar;
        }
        stateChar = 0;
      }
    }

    private Object run() {
      String patternStart = pattern.charAt(0) == '.' ? "" : "(?!\\.)";
      for (int i = 0, len = pattern.length(); i < len; i++) {
        char c = pattern.charAt(i);
        if (escaping && RE_SPECIALS.indexOf(c) >= 0) {
          re += "\\" + c;
          escaping = false;
          continue;
        }
        switch (c) {
          case '/':
            return isSub ? UNDEFINED_SUB : null;
          case '\\':
            clearStateChar();
            escaping = true;
            continue;
          case '?', '*', '+', '@', '!':
            if (inClass) {
              if (c == '!' && i == classStart + 1) {
                c = '^';
              }
              re += c;
              continue;
            }
            if (c == '*' && stateChar == '*') {
              continue;
            }
            clearStateChar();
            stateChar = c;
            continue;
          case '(':
            if (inClass) {
              re += "(";
              continue;
            }
            if (stateChar == 0) {
              re += "\\(";
              continue;
            }
            patternListStack.add(new PatternList(stateChar, re.length()));
            re += stateChar == '!' ? "(?:(?!(?:" : "(?:";
            stateChar = 0;
            continue;
          case ')':
            if (inClass || patternListStack.isEmpty()) {
              re += "\\)";
              continue;
            }
            clearStateChar();
            hasMagic = true;
            PatternList pl = patternListStack.remove(patternListStack.size() - 1);
            re += pl.close();
            if (pl.type == '!') {
              negativeLists.add(pl);
            }
            pl.reEnd = re.length();
            continue;
          case '|':
            if (inClass || patternListStack.isEmpty() || escaping) {
              re += "\\|";
              escaping = false;
              continue;
            }
            clearStateChar();
            re += "|";
            continue;
          case '[':
            clearStateChar();
            if (inClass) {
              re += "\\" + c;
              continue;
            }
            inClass = true;
            classStart = i;
            reClassStart = re.length();
            re += c;
            continue;
          case ']':
            if (i == classStart + 1 || !inClass) {
              re += "\\" + c;
              escaping = false;
              continue;
            }
            String cs = pattern.substring(classStart + 1, i);
            if (!isValidClass(cs)) {
              SubResult sp = (SubResult) parse(cs, true);
              re = jsSlice(re, 0, reClassStart) + "\\[" + sp.re() + "\\]";
              hasMagic = hasMagic || sp.hasMagic();
              inClass = false;
              continue;
            }
            hasMagic = true;
            inClass = false;
            re += c;
            continue;
          default:
            clearStateChar();
            if (escaping) {
              escaping = false;
            } else if (RE_SPECIALS.indexOf(c) >= 0 && !(c == '^' && inClass)) {
              re += "\\";
            }
            re += c;
        }
      }

      // a class left open: "[abc" is "\[abc"
      if (inClass) {
        String cs = pattern.substring(classStart + 1);
        SubResult sp = (SubResult) parse(cs, true);
        re = jsSlice(re, 0, reClassStart) + "\\[" + sp.re();
        hasMagic = hasMagic || sp.hasMagic();
      }

      // an extglob left open: escape the | characters passed through as is
      while (!patternListStack.isEmpty()) {
        PatternList pl = patternListStack.remove(patternListStack.size() - 1);
        String tail = jsSlice(re, pl.reStart + pl.open().length(), Integer.MAX_VALUE);
        Matcher matcher = UNESCAPED_PIPE.matcher(tail);
        StringBuilder escaped = new StringBuilder();
        while (matcher.find()) {
          String evenSlashes = matcher.group(1);
          String slash = matcher.group(2).isEmpty() ? "\\" : matcher.group(2);
          matcher.appendReplacement(escaped, Matcher.quoteReplacement(evenSlashes + evenSlashes + slash + "|"));
        }
        matcher.appendTail(escaped);
        tail = escaped.toString();
        String t = pl.type == '*' ? STAR : pl.type == '?' ? QMARK : "\\" + pl.type;
        hasMagic = true;
        re = jsSlice(re, 0, pl.reStart) + t + "\\(" + tail;
      }

      clearStateChar();
      if (escaping) {
        re += "\\\\";
      }

      boolean addPatternStart = !re.isEmpty() && "[.(".indexOf(re.charAt(0)) >= 0;

      // negative extglobs must look ahead to the end of the pattern
      for (int n = negativeLists.size() - 1; n > -1; n--) {
        PatternList nl = negativeLists.get(n);
        String nlBefore = jsSlice(re, 0, nl.reStart);
        String nlFirst = jsSlice(re, nl.reStart, nl.reEnd - 8);
        String nlLast = jsSlice(re, nl.reEnd - 8, nl.reEnd);
        String nlAfter = jsSlice(re, nl.reEnd, Integer.MAX_VALUE);
        nlLast += nlAfter;
        long openParensBefore = nlBefore.chars().filter(ch -> ch == '(').count();
        String cleanAfter = nlAfter;
        for (int i = 0; i < openParensBefore; i++) {
          cleanAfter = CLOSING_PAREN.matcher(cleanAfter).replaceFirst("");
        }
        nlAfter = cleanAfter;
        String dollar = nlAfter.isEmpty() && !isSub ? "$" : "";
        re = nlBefore + nlFirst + nlAfter + dollar + nlLast;
      }

      if (!re.isEmpty() && hasMagic) {
        re = "(?=.)" + re;
      }
      if (addPatternStart) {
        re = patternStart + re;
      }
      if (isSub) {
        return new SubResult(re, hasMagic);
      }
      if (!hasMagic) {
        return new Literal(globUnescape(pattern));
      }
      try {
        return new Regex(new JsRegExp("^" + re + "$", ""));
      } catch (JsRegExp.SyntaxError invalid) {
        return new Regex(NEVER);
      }
    }
  }

  /** An open extglob ({@code +(}, {@code !(} ...). */
  private static final class PatternList {
    private final char type;
    private final int reStart;
    private int reEnd;

    private PatternList(char type, int reStart) {
      this.type = type;
      this.reStart = reStart;
    }

    private String open() {
      return type == '!' ? "(?:(?!(?:" : "(?:";
    }

    private String close() {
      return switch (type) {
        case '!' -> "))[^/]*?)";
        case '?' -> ")?";
        case '+' -> ")+";
        case '*' -> ")*";
        default -> ")";
      };
    }
  }

  /** {@code RegExp('[' + cs + ']')} does not throw. */
  private static boolean isValidClass(String cs) {
    try {
      new JsRegExp("[" + cs + "]", "");
      return true;
    } catch (JsRegExp.SyntaxError invalid) {
      return false;
    }
  }

  private static String globUnescape(String s) {
    return GLOB_ESCAPE.matcher(s).replaceAll("$1");
  }

  // ---------------------------------------------------------------------------------------------------------------
  // matchOne / _matchGlobstar / _matchGlobStarBodySections / _matchOne
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean matchOne(String[] file, List<Part> pattern) {
    if (pattern.contains(Globstar.INSTANCE)) {
      return matchGlobstar(file, pattern);
    }
    return matchOne(file, pattern, 0, 0);
  }

  private static boolean matchGlobstar(String[] file, List<Part> pattern) {
    int fileIndex = 0;
    int firstgs = pattern.indexOf(Globstar.INSTANCE);
    int lastgs = pattern.lastIndexOf(Globstar.INSTANCE);
    List<Part> head = pattern.subList(0, firstgs);
    List<Part> body = firstgs + 1 <= lastgs ? pattern.subList(firstgs + 1, lastgs) : List.of();
    List<Part> tail = pattern.subList(lastgs + 1, pattern.size());

    if (!head.isEmpty()) {
      String[] fileHead = slice(file, fileIndex, fileIndex + head.size());
      if (!matchOne(fileHead, head, 0, 0)) {
        return false;
      }
      fileIndex += head.size();
    }

    int fileTailMatch = 0;
    if (!tail.isEmpty()) {
      if (tail.size() + fileIndex > file.length) {
        return false;
      }
      int tailStart = file.length - tail.size();
      if (matchOne(file, tail, tailStart, 0)) {
        fileTailMatch = tail.size();
      } else {
        // affordance for a/**/* matching a/b/
        if (!file[file.length - 1].isEmpty() || fileIndex + tail.size() == file.length) {
          return false;
        }
        tailStart--;
        if (!matchOne(file, tail, tailStart, 0)) {
          return false;
        }
        fileTailMatch = tail.size() + 1;
      }
    }

    if (body.isEmpty()) {
      boolean sawSome = fileTailMatch != 0;
      for (int i = fileIndex; i < file.length - fileTailMatch; i++) {
        String f = file[i];
        sawSome = true;
        if (isBadDot(f)) {
          return false;
        }
      }
      return sawSome;
    }

    List<List<Part>> segmentParts = new ArrayList<>();
    segmentParts.add(new ArrayList<>());
    int nonGsParts = 0;
    List<Integer> nonGsPartsSums = new ArrayList<>(List.of(0));
    for (Part b : body) {
      if (b == Globstar.INSTANCE) {
        nonGsPartsSums.add(nonGsParts);
        segmentParts.add(new ArrayList<>());
      } else {
        segmentParts.get(segmentParts.size() - 1).add(b);
        nonGsParts++;
      }
    }
    int idx = segmentParts.size() - 1;
    int fileLength = file.length - fileTailMatch;
    List<BodySegment> bodySegments = new ArrayList<>();
    for (List<Part> parts : segmentParts) {
      bodySegments.add(new BodySegment(parts, fileLength - (nonGsPartsSums.get(idx--) + parts.size())));
    }
    return Boolean.TRUE.equals(matchGlobStarBodySections(file, bodySegments, fileIndex, 0, 0, fileTailMatch != 0));
  }

  private record BodySegment(List<Part> body, int after) {}

  /** {@code true} match, {@code false} no match, {@code null} no match and stop trying. */
  private static Boolean matchGlobStarBodySections(
      String[] file, List<BodySegment> bodySegments, int start, int bodyIndex, int globStarDepth, boolean sawTail) {
    int fileIndex = start;
    if (bodyIndex >= bodySegments.size()) {
      boolean saw = sawTail;
      for (int i = fileIndex; i < file.length; i++) {
        saw = true;
        if (isBadDot(file[i])) {
          return false;
        }
      }
      return saw;
    }
    BodySegment bs = bodySegments.get(bodyIndex);
    List<Part> body = bs.body();
    while (fileIndex <= bs.after()) {
      boolean m = matchOne(slice(file, 0, fileIndex + body.size()), body, fileIndex, 0);
      // past the recursion limit: no match (a deliberate false negative of minimatch 3.1.5)
      if (m && globStarDepth < MAX_GLOBSTAR_RECURSION) {
        Boolean sub = matchGlobStarBodySections(file, bodySegments, fileIndex + body.size(), bodyIndex + 1,
            globStarDepth + 1, sawTail);
        if (!Boolean.FALSE.equals(sub)) {
          return sub;
        }
      }
      if (fileIndex >= file.length) {
        throw new IllegalArgumentException("Cannot read properties of undefined (reading 'charAt')");
      }
      if (isBadDot(file[fileIndex])) {
        return false;
      }
      fileIndex++;
    }
    return null;
  }

  private static boolean matchOne(String[] file, List<Part> pattern, int fileIndex, int patternIndex) {
    int fi = fileIndex;
    int pi = patternIndex;
    int fl = file.length;
    int pl = pattern.size();
    for (; fi < fl && pi < pl; fi++, pi++) {
      Part p = pattern.get(pi);
      String f = file[fi];
      boolean hit = switch (p) {
        case Globstar globstar -> false;
        case Literal literal -> f.equals(literal.value());
        case Regex regex -> regex.regex().test(f);
      };
      if (!hit) {
        return false;
      }
    }
    if (fi == fl && pi == pl) {
      return true;
    } else if (fi == fl) {
      return false;
    } else if (pi == pl) {
      // a/* matches a/b/: only the very last empty segment of a path with a trailing slash may be left
      return fi == fl - 1 && file[fi].isEmpty();
    }
    throw new IllegalStateException("wtf?");
  }

  /** {@code f === '.' || f === '..' || (!options.dot && f.charAt(0) === '.')}. */
  private static boolean isBadDot(String f) {
    return f.startsWith(".");
  }

  private static String[] slice(String[] array, int start, int end) {
    int from = Math.min(Math.max(start, 0), array.length);
    int to = Math.min(Math.max(end, 0), array.length);
    return to <= from ? new String[0] : Arrays.copyOfRange(array, from, to);
  }

  /** JavaScript's {@code String.prototype.slice(start, end)} (negative positions count from the end). */
  private static String jsSlice(String s, int start, int end) {
    int length = s.length();
    int from = start < 0 ? Math.max(length + start, 0) : Math.min(start, length);
    int to = end < 0 ? Math.max(length + end, 0) : Math.min(end, length);
    return to <= from ? "" : s.substring(from, to);
  }

  /** JavaScript's {@code String.prototype.trim()} (white space and line terminators, {@code ﻿} included). */
  static String jsTrim(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && isJsWhitespace(s.charAt(start))) {
      start++;
    }
    while (end > start && isJsWhitespace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(start, end);
  }

  private static boolean isJsWhitespace(char c) {
    return c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r' || c == '﻿' || c == ' '
        || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR;
  }
}
