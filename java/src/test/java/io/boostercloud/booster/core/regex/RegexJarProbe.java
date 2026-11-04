package io.boostercloud.booster.core.regex;

/** Separate-JVM probe whose only production classpath entry is the assembled shaded JAR. */
public final class RegexJarProbe {
  public static void main(String[] args) {
    if (!EcmaRegex.matches("foobar", "(?<=foo)bar", false)
        || !EcmaRegex.matches("aaaa", "^(a+)\\1$", false)
        || EcmaRegex.matches("ſ", "s", true)) throw new AssertionError("Packaged ECMAScript regex semantics failed");
    System.out.println("regex-jar-ok");
  }
}
