package com.boostercloud.framework.core.services.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of {@code pluralize} of the {@code inflected} package (version 2.1.0, default {@code en} locale), which the
 * GraphQL layer uses to name the plural filter queries ({@code <ReadModel>s}), the paginated queries
 * ({@code List<ReadModel>s}) and the filtered subscriptions. The generated names are public API, so the rules are
 * reproduced exactly: the uncountable words, the irregular words, and the plural rules searched from the most
 * recently registered one, the first match replacing (JavaScript {@code String#replace} with a non-global regular
 * expression, i.e. the first occurrence).
 */
public final class Inflection {

  private static final Pattern LAST_WORD = Pattern.compile("\\b\\w+$");

  private static final Set<String> UNCOUNTABLES = Set.of(
      "equipment", "information", "rice", "money", "species", "series", "fish", "sheep", "jeans", "police");

  /** The plural rules in search order (TypeScript {@code unshift}s each new rule to the front). */
  private static final List<Rule> PLURALS = buildPlurals();

  private Inflection() {}

  /**
   * {@code inflected.pluralize(word)}.
   *
   * @param word a word, usually a class name such as {@code CartReadModel}
   * @return its plural ({@code CartReadModels}); uncountable words are returned unchanged
   */
  public static String pluralize(String word) {
    String result = String.valueOf(word);
    if (result.isEmpty()) {
      return result;
    }
    Matcher lastWord = LAST_WORD.matcher(result.toLowerCase(Locale.ROOT));
    if (lastWord.find() && UNCOUNTABLES.contains(lastWord.group())) {
      return result;
    }
    for (Rule rule : PLURALS) {
      Matcher matcher = rule.pattern().matcher(result);
      if (matcher.find()) {
        return matcher.replaceFirst(rule.replacement());
      }
    }
    return result;
  }

  private record Rule(Pattern pattern, String replacement) {}

  private static List<Rule> buildPlurals() {
    List<Rule> rules = new ArrayList<>();
    plural(rules, "$", "s", false);
    plural(rules, "s$", "s", true);
    plural(rules, "^(ax|test)is$", "$1es", true);
    plural(rules, "(octop|vir)us$", "$1i", true);
    plural(rules, "(octop|vir)i$", "$1i", true);
    plural(rules, "(alias|status)$", "$1es", true);
    plural(rules, "(bu)s$", "$1ses", true);
    plural(rules, "(buffal|tomat)o$", "$1oes", true);
    plural(rules, "([ti])um$", "$1a", true);
    plural(rules, "([ti])a$", "$1a", true);
    plural(rules, "sis$", "ses", true);
    plural(rules, "(?:([^f])fe|([lr])f)$", "$1$2ves", true);
    plural(rules, "(hive)$", "$1s", true);
    plural(rules, "([^aeiouy]|qu)y$", "$1ies", true);
    plural(rules, "(x|ch|ss|sh)$", "$1es", true);
    plural(rules, "(matr|vert|ind)(?:ix|ex)$", "$1ices", true);
    plural(rules, "^(m|l)ouse$", "$1ice", true);
    plural(rules, "^(m|l)ice$", "$1ice", true);
    plural(rules, "^(ox)$", "$1en", true);
    plural(rules, "^(oxen)$", "$1", true);
    plural(rules, "(quiz)$", "$1zes", true);
    irregular(rules, "person", "people");
    irregular(rules, "man", "men");
    irregular(rules, "child", "children");
    irregular(rules, "sex", "sexes");
    irregular(rules, "move", "moves");
    irregular(rules, "zombie", "zombies");
    return List.copyOf(rules);
  }

  private static void plural(List<Rule> rules, String regex, String replacement, boolean caseInsensitive) {
    Pattern pattern = caseInsensitive ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE) : Pattern.compile(regex);
    rules.addFirst(new Rule(pattern, replacement));
  }

  /** {@code Inflections#irregular} when both forms start with the same letter (true for the defaults). */
  private static void irregular(List<Rule> rules, String singular, String plural) {
    String s0 = singular.substring(0, 1);
    String sRest = singular.substring(1);
    String p0 = plural.substring(0, 1);
    String pRest = plural.substring(1);
    plural(rules, "(" + s0 + ")" + sRest + "$", "$1" + pRest, true);
    plural(rules, "(" + p0 + ")" + pRest + "$", "$1" + pRest, true);
  }
}
