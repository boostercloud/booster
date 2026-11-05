package io.boostercloud.booster.transport;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** English plural rules translated from inflected 2.1.0, the pinned TypeScript dependency.
 * Copyright (c) 2014-2017 Martin Andert; (c) 2005-2017 David Heinemeier Hansson;
 * (c) 2008 The Ruby I18n team. MIT license: META-INF/licenses/inflected-MIT.txt.
 */
final class GraphQLNames {
    private GraphQLNames() {}
    private record Rule(Pattern pattern, String replacement) {}
    private static final Set<String> UNCOUNTABLE = Set.of("equipment", "information", "rice", "money", "species", "series", "fish", "sheep", "jeans", "police");
    private static final Pattern LAST_WORD = Pattern.compile("\\b\\w+$");
    private static final List<Rule> RULES = List.of(
        new Rule(Pattern.compile("(z)ombies$", Pattern.CASE_INSENSITIVE), "$1ombies"),
        new Rule(Pattern.compile("(z)ombie$", Pattern.CASE_INSENSITIVE), "$1ombies"),
        new Rule(Pattern.compile("(m)oves$", Pattern.CASE_INSENSITIVE), "$1oves"),
        new Rule(Pattern.compile("(m)ove$", Pattern.CASE_INSENSITIVE), "$1oves"),
        new Rule(Pattern.compile("(s)exes$", Pattern.CASE_INSENSITIVE), "$1exes"),
        new Rule(Pattern.compile("(s)ex$", Pattern.CASE_INSENSITIVE), "$1exes"),
        new Rule(Pattern.compile("(c)hildren$", Pattern.CASE_INSENSITIVE), "$1hildren"),
        new Rule(Pattern.compile("(c)hild$", Pattern.CASE_INSENSITIVE), "$1hildren"),
        new Rule(Pattern.compile("(m)en$", Pattern.CASE_INSENSITIVE), "$1en"),
        new Rule(Pattern.compile("(m)an$", Pattern.CASE_INSENSITIVE), "$1en"),
        new Rule(Pattern.compile("(p)eople$", Pattern.CASE_INSENSITIVE), "$1eople"),
        new Rule(Pattern.compile("(p)erson$", Pattern.CASE_INSENSITIVE), "$1eople"),
        new Rule(Pattern.compile("(quiz)$", Pattern.CASE_INSENSITIVE), "$1zes"),
        new Rule(Pattern.compile("^(oxen)$", Pattern.CASE_INSENSITIVE), "$1"),
        new Rule(Pattern.compile("^(ox)$", Pattern.CASE_INSENSITIVE), "$1en"),
        new Rule(Pattern.compile("^(m|l)ice$", Pattern.CASE_INSENSITIVE), "$1ice"),
        new Rule(Pattern.compile("^(m|l)ouse$", Pattern.CASE_INSENSITIVE), "$1ice"),
        new Rule(Pattern.compile("(matr|vert|ind)(?:ix|ex)$", Pattern.CASE_INSENSITIVE), "$1ices"),
        new Rule(Pattern.compile("(x|ch|ss|sh)$", Pattern.CASE_INSENSITIVE), "$1es"),
        new Rule(Pattern.compile("([^aeiouy]|qu)y$", Pattern.CASE_INSENSITIVE), "$1ies"),
        new Rule(Pattern.compile("(hive)$", Pattern.CASE_INSENSITIVE), "$1s"),
        new Rule(Pattern.compile("(?:([^f])fe|([lr])f)$", Pattern.CASE_INSENSITIVE), "$1$2ves"),
        new Rule(Pattern.compile("sis$", Pattern.CASE_INSENSITIVE), "ses"),
        new Rule(Pattern.compile("([ti])a$", Pattern.CASE_INSENSITIVE), "$1a"),
        new Rule(Pattern.compile("([ti])um$", Pattern.CASE_INSENSITIVE), "$1a"),
        new Rule(Pattern.compile("(buffal|tomat)o$", Pattern.CASE_INSENSITIVE), "$1oes"),
        new Rule(Pattern.compile("(bu)s$", Pattern.CASE_INSENSITIVE), "$1ses"),
        new Rule(Pattern.compile("(alias|status)$", Pattern.CASE_INSENSITIVE), "$1es"),
        new Rule(Pattern.compile("(octop|vir)i$", Pattern.CASE_INSENSITIVE), "$1i"),
        new Rule(Pattern.compile("(octop|vir)us$", Pattern.CASE_INSENSITIVE), "$1i"),
        new Rule(Pattern.compile("^(ax|test)is$", Pattern.CASE_INSENSITIVE), "$1es"),
        new Rule(Pattern.compile("s$", Pattern.CASE_INSENSITIVE), "s"),
        new Rule(Pattern.compile("$", 0), "s"));
    static String plural(String word) {
        if (word.isEmpty()) return word;
        var last = LAST_WORD.matcher(word.toLowerCase(Locale.ROOT));
        if (last.find() && UNCOUNTABLE.contains(last.group())) return word;
        for (Rule rule : RULES) {
            var matcher = rule.pattern().matcher(word);
            if (matcher.find()) return matcher.replaceFirst(rule.replacement());
        }
        return word;
    }
}
