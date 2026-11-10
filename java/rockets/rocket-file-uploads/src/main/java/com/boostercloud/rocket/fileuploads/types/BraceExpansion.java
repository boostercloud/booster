package com.boostercloud.rocket.fileuploads.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Port of the npm packages {@code brace-expansion} 1.1.16 and {@code balanced-match} 1.0.2, used by minimatch 3.1.5
 * to expand {@code {a,b}} sets and {@code {1..3}} / {@code {a..c}} sequences with Bash's rules: {@code {a}} stays
 * literal, a leading {@code {}} is not expanded, {@code \{}, {@code \}}, {@code \,} and {@code \.} are escapes,
 * {@code ${...}} is kept, a zero step counts as 1. minimatch calls it without the {@code max} option, so the number
 * of expansions is unbounded, as in JavaScript. Pinned against the JavaScript package by
 * {@code BraceExpansionDifferentialTest}.
 *
 * <p>Java difference: numbers that do not fit a {@code long} are treated like letters (JavaScript switches to
 * floating point); JavaScript's {@code $} and {@code .} regular expression semantics are kept ({@code \z},
 * no line terminators).
 */
final class BraceExpansion {

  private static final String TOKEN = Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE);
  private static final String ESC_SLASH = "\0SLASH" + TOKEN + "\0";
  private static final String ESC_OPEN = "\0OPEN" + TOKEN + "\0";
  private static final String ESC_CLOSE = "\0CLOSE" + TOKEN + "\0";
  private static final String ESC_COMMA = "\0COMMA" + TOKEN + "\0";
  private static final String ESC_PERIOD = "\0PERIOD" + TOKEN + "\0";

  private static final Pattern NUMERIC_SEQUENCE = Pattern.compile("^-?\\d+\\.\\.-?\\d+(?:\\.\\.-?\\d+)?\\z");
  private static final Pattern ALPHA_SEQUENCE = Pattern.compile("^[a-zA-Z]\\.\\.[a-zA-Z](?:\\.\\.-?\\d+)?\\z");
  /** {@code /,(?!,).*\}/}: JavaScript's {@code .} excludes line terminators. */
  private static final Pattern COMMA_THEN_CLOSE = Pattern.compile(",(?!,)[^\\n\\r\\u2028\\u2029]*\\}");
  private static final Pattern PADDED = Pattern.compile("^-?0\\d");

  private BraceExpansion() {}

  /** The result of {@code balanced('{', '}', str)}. */
  record Balanced(int start, int end, String pre, String body, String post) {}

  /**
   * {@code expandTop(str)}.
   *
   * @param str a pattern
   * @return its expansions ({@code []} for an empty pattern)
   */
  static List<String> expand(String str) {
    if (str.isEmpty()) {
      return List.of();
    }
    String source = str;
    if (source.startsWith("{}")) {
      source = "\\{\\}" + source.substring(2);
    }
    List<String> expansions = expand(escapeBraces(source), true);
    List<String> result = new ArrayList<>(expansions.size());
    for (String expansion : expansions) {
      result.add(unescapeBraces(expansion));
    }
    return result;
  }

  private static String escapeBraces(String str) {
    return str.replace("\\\\", ESC_SLASH).replace("\\{", ESC_OPEN).replace("\\}", ESC_CLOSE)
        .replace("\\,", ESC_COMMA).replace("\\.", ESC_PERIOD);
  }

  private static String unescapeBraces(String str) {
    return str.replace(ESC_SLASH, "\\").replace(ESC_OPEN, "{").replace(ESC_CLOSE, "}")
        .replace(ESC_COMMA, ",").replace(ESC_PERIOD, ".");
  }

  private static List<String> expand(String source, boolean top) {
    String str = source;
    boolean isTop = top;
    Balanced m;
    boolean isAlphaSequence;
    boolean isSequence;
    // The {a},b} rewrite restarts with isTop = true: a loop in 1.1.16, so long runs cannot exhaust the stack
    while (true) {
      m = balanced("{", "}", str);
      if (m == null || m.pre().endsWith("$")) {
        return List.of(str);
      }
      boolean isNumericSequence = NUMERIC_SEQUENCE.matcher(m.body()).find();
      isAlphaSequence = ALPHA_SEQUENCE.matcher(m.body()).find();
      isSequence = isNumericSequence || isAlphaSequence;
      boolean isOptions = m.body().indexOf(',') >= 0;
      if (isSequence || isOptions) {
        break;
      }
      if (COMMA_THEN_CLOSE.matcher(m.post()).find()) {
        str = m.pre() + "{" + m.body() + ESC_CLOSE + m.post();
        isTop = true;
        continue;
      }
      return List.of(str);
    }

    List<String> n;
    if (isSequence) {
      n = Arrays.asList(m.body().split("\\.\\.", -1));
    } else {
      n = parseCommaParts(m.body());
      if (n.size() == 1) {
        List<String> embraced = new ArrayList<>();
        for (String expansion : expand(n.get(0), false)) {
          embraced.add("{" + expansion + "}");
        }
        n = embraced;
        if (n.size() == 1) {
          List<String> post = m.post().isEmpty() ? List.of("") : expand(m.post(), false);
          List<String> result = new ArrayList<>();
          for (String p : post) {
            result.add(m.pre() + n.get(0) + p);
          }
          return result;
        }
      }
    }

    String pre = m.pre();
    List<String> post = m.post().isEmpty() ? List.of("") : expand(m.post(), false);
    List<String> values = new ArrayList<>();
    if (isSequence) {
      long x = numeric(n.get(0));
      long y = numeric(n.get(1));
      int width = Math.max(n.get(0).length(), n.get(1).length());
      long increment = n.size() == 3 ? Math.max(Math.abs(numeric(n.get(2))), 1) : 1;
      boolean reverse = y < x;
      if (reverse) {
        increment = -increment;
      }
      boolean pad = n.stream().anyMatch(element -> PADDED.matcher(element).find());
      for (long i = x; reverse ? i >= y : i <= y; i += increment) {
        String c;
        if (isAlphaSequence) {
          c = String.valueOf((char) i);
          if (c.equals("\\")) {
            c = "";
          }
        } else {
          c = Long.toString(i);
          if (pad) {
            int need = width - c.length();
            if (need > 0) {
              String zeros = "0".repeat(need);
              c = i < 0 ? "-" + zeros + c.substring(1) : zeros + c;
            }
          }
        }
        values.add(c);
      }
    } else {
      for (String element : n) {
        values.addAll(expand(element, false));
      }
    }

    List<String> expansions = new ArrayList<>();
    for (String value : values) {
      for (String p : post) {
        String expansion = pre + value + p;
        if (!isTop || isSequence || !expansion.isEmpty()) {
          expansions.add(expansion);
        }
      }
    }
    return expansions;
  }

  /** {@code parseCommaParts(str)}: {@code str.split(',')} keeping nested brace sets whole. */
  private static List<String> parseCommaParts(String str) {
    if (str.isEmpty()) {
      return new ArrayList<>(List.of(""));
    }
    Balanced m = balanced("{", "}", str);
    if (m == null) {
      return new ArrayList<>(Arrays.asList(str.split(",", -1)));
    }
    List<String> p = new ArrayList<>(Arrays.asList(m.pre().split(",", -1)));
    int last = p.size() - 1;
    p.set(last, p.get(last) + "{" + m.body() + "}");
    List<String> postParts = parseCommaParts(m.post());
    if (!m.post().isEmpty()) {
      p.set(last, p.get(last) + postParts.remove(0));
      p.addAll(postParts);
    }
    return p;
  }

  /** {@code numeric(str)}: {@code parseInt(str, 10) == str ? parseInt(str, 10) : str.charCodeAt(0)}. */
  private static long numeric(String str) {
    try {
      return Long.parseLong(str);
    } catch (NumberFormatException e) {
      return str.isEmpty() ? 0 : str.charAt(0);
    }
  }

  /**
   * {@code balanced(a, b, str)} of {@code balanced-match}.
   *
   * @param a the opening string
   * @param b the closing string
   * @param str the text
   * @return the first balanced pair, or {@code null}
   */
  static Balanced balanced(String a, String b, String str) {
    int[] r = range(a, b, str);
    if (r == null) {
      return null;
    }
    return new Balanced(r[0], r[1], str.substring(0, r[0]), str.substring(r[0] + a.length(), r[1]),
        str.substring(r[1] + b.length()));
  }

  private static int[] range(String a, String b, String str) {
    int ai = str.indexOf(a);
    int bi = str.indexOf(b, ai + 1);
    int i = ai;
    int[] result = null;
    if (ai >= 0 && bi > 0) {
      if (a.equals(b)) {
        return new int[] {ai, bi};
      }
      List<Integer> begs = new ArrayList<>();
      int left = str.length();
      int right = -1;
      while (i >= 0 && result == null) {
        if (i == ai) {
          begs.add(i);
          ai = str.indexOf(a, i + 1);
        } else if (begs.size() == 1) {
          result = new int[] {begs.remove(begs.size() - 1), bi};
        } else {
          int beg = begs.remove(begs.size() - 1);
          if (beg < left) {
            left = beg;
            right = bi;
          }
          bi = str.indexOf(b, i + 1);
        }
        i = ai < bi && ai >= 0 ? ai : bi;
      }
      if (!begs.isEmpty() && right >= 0) {
        result = new int[] {left, right};
      }
    }
    return result;
  }
}
