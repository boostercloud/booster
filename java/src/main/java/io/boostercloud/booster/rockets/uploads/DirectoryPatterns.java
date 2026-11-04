package io.boostercloud.booster.rockets.uploads;

import java.util.*;
import java.util.regex.Pattern;

/** Bounded minimatch-style directory matching, including brace ranges, extglobs and negation. */
public final class DirectoryPatterns {
    private DirectoryPatterns() {}

    static void validatePattern(String pattern) {
        if (pattern == null || pattern.length() > 512 || pattern.startsWith("/") || pattern.chars().anyMatch(c -> c < 32))
            throw new IllegalArgumentException("Invalid directory pattern");
        for (String expanded : expand(pattern, 0)) for (String part : expanded.split("/", -1)) {
            if (part.equals("..") || part.equals(".")) throw new IllegalArgumentException("Unsafe directory pattern");
            if (!part.equals("**")) new SegmentGlob(part);
        }
    }

    public static boolean matches(String source, List<String> patterns) {
        if (source == null || source.startsWith("/") || source.length() > 1024) return false;
        try { FilePaths.directory(source); } catch (IllegalArgumentException e) { return false; }
        for (String original : patterns) {
            validatePattern(original);
            if (original.startsWith("#")) continue;
            String pattern = original;
            boolean negate = false;
            while (pattern.startsWith("!")) { negate = !negate; pattern = pattern.substring(1); }
            boolean found = false;
            for (String expanded : expand(pattern, 0)) {
                String[] parts = source.split("/", -1), glob = expanded.split("/", -1);
                SegmentGlob[] segments = Arrays.stream(glob).map(g -> g.equals("**") ? null : new SegmentGlob(g)).toArray(SegmentGlob[]::new);
                if (match(glob, segments, 0, parts, 0, new Boolean[glob.length + 1][parts.length + 1])) { found = true; break; }
            }
            if (found != negate) return true;
        }
        return false;
    }

    private static boolean match(String[] glob, SegmentGlob[] segments, int g, String[] parts, int p, Boolean[][] memo) {
        if (memo[g][p] != null) return memo[g][p];
        boolean result;
        if (g == glob.length) result = p == parts.length || (p == parts.length - 1 && parts[p].isEmpty());
        else if (glob[g].equals("**")) {
            result = g == glob.length - 1 ? p < parts.length && Arrays.stream(parts, p, parts.length).noneMatch(part -> part.startsWith("."))
                    : match(glob, segments, g + 1, parts, p, memo)
                    || (p < parts.length && !parts[p].startsWith(".") && match(glob, segments, g, parts, p + 1, memo));
        } else result = p < parts.length && segments[g].matches(parts[p]) && match(glob, segments, g + 1, parts, p + 1, memo);
        return memo[g][p] = result;
    }

    private static List<String> expand(String value, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Directory braces are too deeply nested");
        int start = -1, end = -1, level = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '{') { if (level++ == 0) start = i; }
            else if (c == '}' && level > 0 && --level == 0) { end = i; break; }
        }
        if (end < 0) return List.of(value);
        String middle = value.substring(start + 1, end);
        var choices = new ArrayList<String>();
        int from = 0; level = 0;
        for (int i = 0; i < middle.length(); i++) {
            char c = middle.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '{') level++; else if (c == '}') level--;
            else if (c == ',' && level == 0) { choices.add(middle.substring(from, i)); from = i + 1; }
        }
        if (!choices.isEmpty()) choices.add(middle.substring(from));
        else {
            var range = Pattern.compile("(-?[0-9]+|[a-zA-Z])\\.\\.(-?[0-9]+|[a-zA-Z])(?:\\.\\.(-?[0-9]+))?").matcher(middle);
            if (range.matches()) {
                boolean numeric = range.group(1).matches("-?[0-9]+") && range.group(2).matches("-?[0-9]+");
                if (!numeric && !(range.group(1).length() == 1 && range.group(2).length() == 1)) return List.of(value);
                long first = numeric ? Long.parseLong(range.group(1)) : range.group(1).charAt(0);
                long last = numeric ? Long.parseLong(range.group(2)) : range.group(2).charAt(0);
                long step = range.group(3) == null ? 1 : Math.abs(Long.parseLong(range.group(3)));
                if (step == 0) step = 1;
                if (first > last) step = -step;
                int width = numeric && (range.group(1).matches("-?0[0-9]+") || range.group(2).matches("-?0[0-9]+"))
                        ? Math.max(range.group(1).length(), range.group(2).length()) : 0;
                for (long number = first; step > 0 ? number <= last : number >= last; number = Math.addExact(number, step)) {
                    if (choices.size() >= 1024) throw new IllegalArgumentException("Directory brace expansion is too large");
                    choices.add(numeric ? width > 0 ? String.format(Locale.ROOT, "%0" + width + "d", number) : Long.toString(number)
                            : Character.toString((char) number));
                    if (number == last) break;
                }
            } else {
                List<String> tail = expand(value.substring(end + 1), depth + 1);
                String prefix = value.substring(0, end + 1);
                return tail.stream().map(suffix -> prefix + suffix).toList();
            }
        }
        var result = new ArrayList<String>();
        for (String choice : choices) for (String expanded : expand(value.substring(0, start) + choice + value.substring(end + 1), depth + 1)) {
            if (result.size() >= 1024) throw new IllegalArgumentException("Directory brace expansion is too large");
            result.add(expanded);
        }
        return result;
    }

    /** Matching uses memoized sets of reachable character positions, avoiding regex backtracking. */
    private static final class SegmentGlob {
        private interface Node {}
        private record Literal(char value) implements Node {}
        private record Any() implements Node {}
        private record Star() implements Node {}
        private record Characters(Pattern pattern) implements Node {}
        private record Sequence(List<Node> nodes) implements Node {}
        private record Choice(List<Node> choices) implements Node {}
        private record Repeat(Node node, int minimum, boolean once) implements Node {}
        private record Negative(Node node) implements Node {}
        private final String source;
        private final Node root;
        private int cursor;
        SegmentGlob(String source) { this.source = source; root = sequence(false, 0); }
        boolean matches(String value) {
            if (value.isEmpty()) return source.isEmpty();
            if (value.startsWith(".") && !(source.startsWith(".") || source.startsWith("\\."))) return false;
            return ends(root, value, 0, new IdentityHashMap<>()).get(value.length());
        }
        private Node sequence(boolean group, int depth) {
            if (depth > 16) throw new IllegalArgumentException("Directory extglob is too deeply nested");
            var nodes = new ArrayList<Node>();
            while (cursor < source.length()) {
                char c = source.charAt(cursor);
                if (group && (c == ')' || c == '|')) break;
                cursor++;
                if (c == '\\' && cursor < source.length()) { nodes.add(new Literal(source.charAt(cursor++))); continue; }
                if ("?*+@!".indexOf(c) >= 0 && cursor < source.length() && source.charAt(cursor) == '(') {
                    int saved = cursor++;
                    var alternatives = new ArrayList<Node>();
                    while (true) {
                        alternatives.add(sequence(true, depth + 1));
                        if (cursor >= source.length() || source.charAt(cursor) != '|') break;
                        cursor++;
                    }
                    if (cursor < source.length() && source.charAt(cursor) == ')') {
                        cursor++; Node choice = new Choice(alternatives);
                        nodes.add(switch (c) { case '@' -> choice; case '!' -> new Negative(choice);
                            case '?' -> new Repeat(choice, 0, true); case '*' -> new Repeat(choice, 0, false);
                            default -> new Repeat(choice, 1, false); });
                        continue;
                    }
                    cursor = saved;
                }
                if (c == '*') nodes.add(new Star());
                else if (c == '?') nodes.add(new Any());
                else if (c == '[') {
                    int end = source.indexOf(']', cursor + (cursor < source.length() && source.charAt(cursor) == '!' ? 1 : 0));
                    if (end >= 0) {
                        String characters = source.substring(cursor, end);
                        if (characters.startsWith("!")) characters = "^" + characters.substring(1);
                        try { nodes.add(new Characters(Pattern.compile("[" + characters + "]"))); cursor = end + 1; continue; }
                        catch (java.util.regex.PatternSyntaxException ignored) { }
                    }
                    nodes.add(new Literal(c));
                } else nodes.add(new Literal(c));
            }
            return new Sequence(nodes);
        }
        private static BitSet ends(Node node, String value, int start, IdentityHashMap<Node, BitSet[]> memo) {
            BitSet[] positions = memo.computeIfAbsent(node, ignored -> new BitSet[value.length() + 1]);
            if (positions[start] != null) return positions[start];
            var result = new BitSet(value.length() + 1);
            if (node instanceof Literal literal) { if (start < value.length() && value.charAt(start) == literal.value()) result.set(start + 1); }
            else if (node instanceof Any) { if (start < value.length()) result.set(start + 1); }
            else if (node instanceof Star) result.set(start, value.length() + 1);
            else if (node instanceof Characters characters) {
                if (start < value.length() && characters.pattern().matcher(value.substring(start, start + 1)).matches()) result.set(start + 1);
            } else if (node instanceof Choice choice) for (Node option : choice.choices()) result.or(ends(option, value, start, memo));
            else if (node instanceof Negative negative) {
                result.set(start, value.length() + 1); result.andNot(ends(negative.node(), value, start, memo));
            } else if (node instanceof Sequence sequence) {
                result.set(start);
                for (Node part : sequence.nodes()) {
                    var next = new BitSet(value.length() + 1);
                    for (int position = result.nextSetBit(0); position >= 0; position = result.nextSetBit(position + 1)) next.or(ends(part, value, position, memo));
                    result = next;
                }
            } else if (node instanceof Repeat repeat) {
                var pending = new ArrayDeque<Integer>();
                if (repeat.minimum() == 0) result.set(start);
                BitSet first = ends(repeat.node(), value, start, memo);
                result.or(first);
                if (!repeat.once()) {
                    for (int position = first.nextSetBit(0); position >= 0; position = first.nextSetBit(position + 1)) pending.add(position);
                    while (!pending.isEmpty()) {
                        BitSet next = (BitSet) ends(repeat.node(), value, pending.removeFirst(), memo).clone(); next.andNot(result);
                        result.or(next);
                        for (int position = next.nextSetBit(0); position >= 0; position = next.nextSetBit(position + 1)) pending.add(position);
                    }
                }
            }
            return positions[start] = result;
        }
    }
}
