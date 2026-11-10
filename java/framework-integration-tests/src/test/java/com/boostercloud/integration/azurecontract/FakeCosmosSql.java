package com.boostercloud.integration.azurecontract;

import com.boostercloud.framework.types.BoosterJson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The subset of the Cosmos DB SQL dialect that {@link FakeAzureServices} executes: what the Azure provider, its test
 * helper and the client-side query pipeline send for the application under test, parsed and evaluated the way the
 * service does, so that the fake answers from the documents it stores instead of from canned responses.
 *
 * <p>Supported: {@code SELECT [DISTINCT] [VALUE] [TOP n] <* | expressions [AS alias]> FROM c [WHERE <condition>]
 * [ORDER BY <path> [ASC|DESC], ...] [OFFSET n LIMIT m]}; conditions with {@code AND}, {@code OR}, {@code NOT},
 * parentheses, {@code IS_DEFINED(...)}, {@code IN (...)}, {@code =}, {@code !=}, {@code <>}, {@code <}, {@code <=}, {@code >},
 * {@code >=}; operands that are property paths ({@code c.a}, {@code c["a"]["b"]}), parameters ({@code @p}), string,
 * number, boolean and {@code null} literals, {@code COUNT(...)}, and the array/object constructors the gateway uses in
 * its rewritten {@code ORDER BY} queries ({@code [{"item": c.x}] AS orderByItems}). Anything else is rejected with an
 * {@link UnsupportedQueryException}, which the fake reports as a failure, so a query outside this subset makes the
 * contract tests fail loudly instead of returning a wrong result.
 *
 * <p>Semantics follow Cosmos DB: a comparison between values of different types (or with an undefined property) is
 * undefined, so the document is filtered out; {@code ORDER BY} sorts undefined, null, booleans, numbers and strings in
 * that order; aggregates return one value.
 */
final class FakeCosmosSql {

  /** The value of a property a document does not have. */
  static final Object UNDEFINED = new Object() {
    @Override
    public String toString() {
      return "undefined";
    }
  };

  /** Placeholder of the rewritten {@code ORDER BY} queries, replaced by the client with {@code true}. */
  static final String ORDER_BY_FILTER = "{documentdb-formattableorderbyquery-filter}";

  /** A query outside the supported subset. */
  static final class UnsupportedQueryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UnsupportedQueryException(String message) {
      super(message);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Syntax tree

  /** An expression of the dialect. */
  sealed interface Expression permits Path, Literal, Parameter, Count, ArrayOf, ObjectOf, Unary, Binary, IsDefined, In {}

  /**
   * {@code c}, {@code c.a}, {@code c["a"]}.
   *
   * @param names the property names after the root alias
   * @param text the source text
   */
  record Path(List<String> names, String text) implements Expression {}

  /**
   * A literal.
   *
   * @param value the value
   */
  record Literal(Object value) implements Expression {}

  /**
   * A query parameter.
   *
   * @param name the name, with its {@code @}
   */
  record Parameter(String name) implements Expression {}

  /**
   * {@code COUNT(x)}.
   *
   * @param argument the argument
   */
  record Count(Expression argument) implements Expression {}

  /**
   * {@code [a, b]}.
   *
   * @param items the items
   */
  record ArrayOf(List<Expression> items) implements Expression {}

  /**
   * {@code {"a": x}}.
   *
   * @param properties the properties
   */
  record ObjectOf(Map<String, Expression> properties) implements Expression {}

  /**
   * {@code NOT x}.
   *
   * @param operand the operand
   */
  record Unary(Expression operand) implements Expression {}

  /**
   * {@code AND}, {@code OR} and the comparisons.
   *
   * @param operator the operator ({@code <>} is normalised to {@code !=})
   * @param left the left operand
   * @param right the right operand
   */
  record Binary(String operator, Expression left, Expression right) implements Expression {}

  /**
   * {@code IS_DEFINED(x)}.
   *
   * @param argument the argument
   */
  record IsDefined(Expression argument) implements Expression {}

  /**
   * {@code x IN (a, b)}.
   *
   * @param value the tested value
   * @param candidates the list
   */
  record In(Expression value, List<Expression> candidates) implements Expression {}

  /**
   * An item of the select list.
   *
   * @param expression the expression
   * @param alias the {@code AS} alias, or {@code null}
   */
  record SelectItem(Expression expression, String alias) {}

  /**
   * An {@code ORDER BY} item.
   *
   * @param path the property
   * @param descending {@code DESC}
   * @param text the source text
   */
  record OrderItem(Path path, boolean descending, String text) {}

  /**
   * A parsed query.
   *
   * @param distinct {@code SELECT DISTINCT}
   * @param value {@code SELECT VALUE}
   * @param top {@code TOP n}, or {@code null}
   * @param star {@code SELECT *}
   * @param select the select list (empty for {@code *})
   * @param where the condition, or {@code null}
   * @param whereText the source text of the condition, or {@code null}
   * @param orderBy the {@code ORDER BY} items
   * @param offset {@code OFFSET n}, or {@code null}
   * @param limit {@code LIMIT n}, or {@code null}
   */
  record Query(boolean distinct, boolean value, Integer top, boolean star, List<SelectItem> select, Expression where,
      String whereText, List<OrderItem> orderBy, Integer offset, Integer limit) {

    /**
     * @return whether the select list is an aggregate ({@code COUNT})
     */
    boolean isAggregate() {
      return select.stream().anyMatch(item -> item.expression() instanceof Count);
    }

    /**
     * @return whether the gateway can run this query across partitions by itself (otherwise it answers 400/1004)
     */
    boolean isGatewayServable() {
      return !distinct && top == null && orderBy.isEmpty() && offset == null && limit == null && !isAggregate();
    }
  }

  private FakeCosmosSql() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Parsing

  /**
   * @param text a query
   * @return the parsed query
   * @throws UnsupportedQueryException when the query is outside the supported subset
   */
  static Query parse(String text) {
    return new Parser(text).query();
  }

  private record Token(String kind, String text, int start, int end) {
    boolean is(String keyword) {
      return kind.equals("word") && text.equalsIgnoreCase(keyword);
    }

    boolean isSymbol(String symbol) {
      return kind.equals("symbol") && text.equals(symbol);
    }
  }

  private static final class Parser {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int position;

    Parser(String source) {
      this.source = Objects.requireNonNull(source, "query");
      tokenize();
    }

    private void tokenize() {
      int i = 0;
      while (i < source.length()) {
        char c = source.charAt(i);
        if (Character.isWhitespace(c)) {
          i++;
        } else if (c == '"' || c == '\'') {
          int end = i + 1;
          StringBuilder value = new StringBuilder();
          while (end < source.length() && source.charAt(end) != c) {
            if (source.charAt(end) == '\\' && end + 1 < source.length()) {
              end++;
            }
            value.append(source.charAt(end));
            end++;
          }
          if (end >= source.length()) {
            throw new UnsupportedQueryException("Unterminated string in query: " + source);
          }
          tokens.add(new Token("string", value.toString(), i, end + 1));
          i = end + 1;
        } else if (Character.isDigit(c)
            || (c == '-' && i + 1 < source.length() && Character.isDigit(source.charAt(i + 1)))) {
          int end = i + 1;
          while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '.')) {
            end++;
          }
          tokens.add(new Token("number", source.substring(i, end), i, end));
          i = end;
        } else if (c == '@' || Character.isLetter(c) || c == '_') {
          int end = i + 1;
          while (end < source.length()
              && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) {
            end++;
          }
          tokens.add(new Token(c == '@' ? "parameter" : "word", source.substring(i, end), i, end));
          i = end;
        } else if (c == '{' && source.startsWith(ORDER_BY_FILTER, i)) {
          throw new UnsupportedQueryException("The ORDER BY filter placeholder was not replaced: " + source);
        } else {
          String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
          if (two.equals("!=") || two.equals("<>") || two.equals("<=") || two.equals(">=")) {
            tokens.add(new Token("symbol", two, i, i + 2));
            i += 2;
          } else if ("()[]{},.:*=<>".indexOf(c) >= 0) {
            tokens.add(new Token("symbol", String.valueOf(c), i, i + 1));
            i++;
          } else {
            throw new UnsupportedQueryException("Unexpected character '" + c + "' in query: " + source);
          }
        }
      }
    }

    private Token peek() {
      return position < tokens.size() ? tokens.get(position) : new Token("end", "", source.length(), source.length());
    }

    private Token next() {
      Token token = peek();
      position++;
      return token;
    }

    private boolean accept(String keyword) {
      if (peek().is(keyword)) {
        position++;
        return true;
      }
      return false;
    }

    private boolean acceptSymbol(String symbol) {
      if (peek().isSymbol(symbol)) {
        position++;
        return true;
      }
      return false;
    }

    private void expect(String keyword) {
      if (!accept(keyword)) {
        throw unsupported("expected " + keyword);
      }
    }

    private void expectSymbol(String symbol) {
      if (!acceptSymbol(symbol)) {
        throw unsupported("expected '" + symbol + "'");
      }
    }

    private UnsupportedQueryException unsupported(String reason) {
      return new UnsupportedQueryException(reason + " at '" + peek().text() + "' in query: " + source);
    }

    private int integer() {
      Token token = next();
      if (!token.kind().equals("number")) {
        throw unsupported("expected a number");
      }
      return Integer.parseInt(token.text());
    }

    Query query() {
      expect("SELECT");
      boolean distinct = accept("DISTINCT");
      boolean value = accept("VALUE");
      Integer top = accept("TOP") ? integer() : null;
      boolean star = acceptSymbol("*");
      List<SelectItem> select = new ArrayList<>();
      if (!star) {
        do {
          Expression expression = expression();
          String alias = accept("AS") ? next().text() : null;
          select.add(new SelectItem(expression, alias));
        } while (acceptSymbol(","));
      }
      expect("FROM");
      Token root = next();
      if (!root.is("c")) {
        throw unsupported("only FROM c is supported");
      }
      Expression where = null;
      String whereText = null;
      if (accept("WHERE")) {
        int start = peek().start();
        where = expression();
        whereText = source.substring(start, tokens.get(position - 1).end()).strip();
      }
      List<OrderItem> orderBy = new ArrayList<>();
      if (accept("ORDER")) {
        expect("BY");
        do {
          int start = peek().start();
          Expression expression = primary();
          if (!(expression instanceof Path path)) {
            throw unsupported("ORDER BY supports property paths only");
          }
          boolean descending = false;
          if (accept("DESC")) {
            descending = true;
          } else {
            accept("ASC");
          }
          orderBy.add(new OrderItem(path, descending, source.substring(start, tokens.get(position - 1).end()).strip()));
        } while (acceptSymbol(","));
      }
      Integer offset = null;
      Integer limit = null;
      if (accept("OFFSET")) {
        offset = integer();
        expect("LIMIT");
        limit = integer();
      }
      if (!peek().kind().equals("end")) {
        throw unsupported("unexpected trailing text");
      }
      return new Query(distinct, value, top, star, List.copyOf(select), where, whereText, List.copyOf(orderBy), offset,
          limit);
    }

    private Expression expression() {
      Expression left = conjunction();
      while (accept("OR")) {
        left = new Binary("OR", left, conjunction());
      }
      return left;
    }

    private Expression conjunction() {
      Expression left = negation();
      while (accept("AND")) {
        left = new Binary("AND", left, negation());
      }
      return left;
    }

    private Expression negation() {
      if (accept("NOT")) {
        return new Unary(negation());
      }
      return comparison();
    }

    private Expression comparison() {
      Expression left = primary();
      Token token = peek();
      if (token.kind().equals("symbol") && List.of("=", "!=", "<>", "<", "<=", ">", ">=").contains(token.text())) {
        position++;
        String operator = token.text().equals("<>") ? "!=" : token.text();
        return new Binary(operator, left, primary());
      }
      if (accept("IN")) {
        expectSymbol("(");
        List<Expression> candidates = new ArrayList<>();
        if (!acceptSymbol(")")) {
          do {
            candidates.add(primary());
          } while (acceptSymbol(","));
          expectSymbol(")");
        }
        return new In(left, candidates);
      }
      return left;
    }

    private Expression primary() {
      Token token = next();
      switch (token.kind()) {
        case "string" -> {
          return new Literal(token.text());
        }
        case "number" -> {
          return new Literal(token.text().contains(".") ? (Object) Double.parseDouble(token.text())
              : (Object) Long.parseLong(token.text()));
        }
        case "parameter" -> {
          return new Parameter(token.text());
        }
        case "symbol" -> {
          return symbol(token);
        }
        case "word" -> {
          return word(token);
        }
        default -> throw new UnsupportedQueryException("Unexpected end of query: " + source);
      }
    }

    private Expression symbol(Token token) {
      if (token.text().equals("(")) {
        Expression inner = expression();
        expectSymbol(")");
        return inner;
      }
      if (token.text().equals("[")) {
        List<Expression> items = new ArrayList<>();
        if (!acceptSymbol("]")) {
          do {
            items.add(expression());
          } while (acceptSymbol(","));
          expectSymbol("]");
        }
        return new ArrayOf(items);
      }
      if (token.text().equals("{")) {
        Map<String, Expression> properties = new LinkedHashMap<>();
        if (!acceptSymbol("}")) {
          do {
            Token key = next();
            if (!key.kind().equals("string") && !key.kind().equals("word")) {
              throw unsupported("expected an object key");
            }
            expectSymbol(":");
            properties.put(key.text(), expression());
          } while (acceptSymbol(","));
          expectSymbol("}");
        }
        return new ObjectOf(properties);
      }
      throw new UnsupportedQueryException("Unexpected '" + token.text() + "' in query: " + source);
    }

    private Expression word(Token token) {
      if (token.is("true") || token.is("false")) {
        return new Literal(Boolean.parseBoolean(token.text().toLowerCase(Locale.ROOT)));
      }
      if (token.is("null")) {
        return new Literal(null);
      }
      if (token.is("IS_DEFINED") || token.is("COUNT")) {
        expectSymbol("(");
        Expression argument = expression();
        expectSymbol(")");
        return token.is("COUNT") ? new Count(argument) : new IsDefined(argument);
      }
      if (token.is("c")) {
        return path(token);
      }
      throw new UnsupportedQueryException("Unsupported '" + token.text() + "' in query: " + source);
    }

    private Path path(Token root) {
      List<String> names = new ArrayList<>();
      int end = root.end();
      while (true) {
        if (peek().isSymbol(".")) {
          position++;
          Token name = next();
          if (!name.kind().equals("word")) {
            throw unsupported("expected a property name");
          }
          names.add(name.text());
          end = name.end();
        } else if (peek().isSymbol("[") && position + 1 < tokens.size()
            && tokens.get(position + 1).kind().equals("string")) {
          position++;
          names.add(next().text());
          Token close = next();
          if (!close.isSymbol("]")) {
            throw unsupported("expected ']'");
          }
          end = close.end();
        } else {
          break;
        }
      }
      return new Path(List.copyOf(names), source.substring(root.start(), end));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Evaluation

  /**
   * Runs a query on the documents of one partition key range (or of one logical partition).
   *
   * @param query the query
   * @param documents the documents in scope, in storage order
   * @param parameters the query parameters by name
   * @return the result rows
   */
  static List<Object> execute(Query query, List<Map<String, Object>> documents, Map<String, Object> parameters) {
    List<Map<String, Object>> matching = new ArrayList<>();
    for (Map<String, Object> document : documents) {
      if (query.where() == null || Boolean.TRUE.equals(evaluate(query.where(), document, parameters))) {
        matching.add(document);
      }
    }
    if (!query.orderBy().isEmpty()) {
      Comparator<Map<String, Object>> comparator = null;
      for (OrderItem item : query.orderBy()) {
        Comparator<Map<String, Object>> next = (left, right) ->
            compareForOrder(evaluate(item.path(), left, parameters), evaluate(item.path(), right, parameters));
        if (item.descending()) {
          next = next.reversed();
        }
        comparator = comparator == null ? next : comparator.thenComparing(next);
      }
      matching.sort(comparator);
    }
    List<Object> rows = new ArrayList<>();
    if (query.isAggregate()) {
      if (query.select().size() != 1 || !query.value()) {
        throw new UnsupportedQueryException("Only SELECT VALUE COUNT(...) aggregates are supported");
      }
      Count count = (Count) query.select().getFirst().expression();
      long total = 0;
      for (Map<String, Object> document : matching) {
        if (evaluate(count.argument(), document, parameters) != UNDEFINED) {
          total++;
        }
      }
      rows.add(total);
      return rows;
    }
    for (Map<String, Object> document : matching) {
      Object row = project(query, document, parameters);
      if (row != UNDEFINED) {
        rows.add(row);
      }
    }
    if (query.distinct()) {
      Set<String> seen = new LinkedHashSet<>();
      List<Object> distinct = new ArrayList<>();
      for (Object row : rows) {
        if (seen.add(BoosterJson.toJson(row))) {
          distinct.add(row);
        }
      }
      rows = distinct;
    }
    if (query.offset() != null) {
      int from = Math.min(query.offset(), rows.size());
      int to = Math.min(rows.size(), from + query.limit());
      rows = new ArrayList<>(rows.subList(from, to));
    }
    if (query.top() != null) {
      rows = new ArrayList<>(rows.subList(0, Math.min(rows.size(), query.top())));
    }
    return rows;
  }

  private static Object project(Query query, Map<String, Object> document, Map<String, Object> parameters) {
    if (query.star()) {
      return document;
    }
    if (query.value()) {
      return evaluate(query.select().getFirst().expression(), document, parameters);
    }
    Map<String, Object> row = new LinkedHashMap<>();
    int unnamed = 0;
    for (SelectItem item : query.select()) {
      Object value = evaluate(item.expression(), document, parameters);
      String name = item.alias();
      if (name == null) {
        name = item.expression() instanceof Path path && !path.names().isEmpty()
            ? path.names().getLast()
            : "$" + (++unnamed);
      }
      if (value != UNDEFINED) {
        row.put(name, value);
      }
    }
    return row;
  }

  /**
   * @param expression an expression
   * @param document the document bound to {@code c}
   * @param parameters the query parameters
   * @return the value, {@link #UNDEFINED} for a missing property or an undefined comparison
   */
  static Object evaluate(Expression expression, Map<String, Object> document, Map<String, Object> parameters) {
    return switch (expression) {
      case Path path -> {
        Object current = document;
        for (String name : path.names()) {
          if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
            yield UNDEFINED;
          }
          current = map.get(name);
        }
        yield current;
      }
      case Literal literal -> literal.value();
      case Parameter parameter -> {
        if (!parameters.containsKey(parameter.name())) {
          throw new UnsupportedQueryException("Missing query parameter " + parameter.name());
        }
        yield parameters.get(parameter.name());
      }
      case Count count -> throw new UnsupportedQueryException("COUNT outside the select list");
      case ArrayOf array -> {
        List<Object> values = new ArrayList<>();
        for (Expression item : array.items()) {
          Object value = evaluate(item, document, parameters);
          if (value != UNDEFINED) {
            values.add(value);
          }
        }
        yield values;
      }
      case ObjectOf object -> {
        Map<String, Object> values = new LinkedHashMap<>();
        object.properties().forEach((name, item) -> {
          Object value = evaluate(item, document, parameters);
          if (value != UNDEFINED) {
            values.put(name, value);
          }
        });
        yield values;
      }
      case IsDefined isDefined -> evaluate(isDefined.argument(), document, parameters) != UNDEFINED;
      case Unary unary -> {
        Object operand = evaluate(unary.operand(), document, parameters);
        yield operand instanceof Boolean bool ? (Object) !bool : UNDEFINED;
      }
      case Binary binary -> evaluateBinary(binary, document, parameters);
      case In in -> {
        Object value = evaluate(in.value(), document, parameters);
        if (value == UNDEFINED) {
          yield UNDEFINED;
        }
        boolean found = false;
        for (Expression candidate : in.candidates()) {
          Object option = evaluate(candidate, document, parameters);
          if (option != UNDEFINED && typeRank(option) == typeRank(value)
              && BoosterJson.toJson(option).equals(BoosterJson.toJson(value))) {
            found = true;
          }
        }
        yield found;
      }
    };
  }

  private static Object evaluateBinary(Binary binary, Map<String, Object> document, Map<String, Object> parameters) {
    Object left = evaluate(binary.left(), document, parameters);
    if (binary.operator().equals("AND")) {
      if (Boolean.FALSE.equals(left)) {
        return false;
      }
      Object right = evaluate(binary.right(), document, parameters);
      if (Boolean.FALSE.equals(right)) {
        return false;
      }
      return Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right) ? (Object) true : UNDEFINED;
    }
    if (binary.operator().equals("OR")) {
      if (Boolean.TRUE.equals(left)) {
        return true;
      }
      Object right = evaluate(binary.right(), document, parameters);
      if (Boolean.TRUE.equals(right)) {
        return true;
      }
      return Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right) ? (Object) false : UNDEFINED;
    }
    Object right = evaluate(binary.right(), document, parameters);
    if (left == UNDEFINED || right == UNDEFINED) {
      return UNDEFINED;
    }
    int typeLeft = typeRank(left);
    if (typeLeft != typeRank(right)) {
      // Values of different types are not comparable: the comparison is undefined
      return UNDEFINED;
    }
    if (binary.operator().equals("=") || binary.operator().equals("!=")) {
      boolean equal = typeLeft >= 5
          ? BoosterJson.toJson(left).equals(BoosterJson.toJson(right))
          : compareSameType(left, right) == 0;
      return binary.operator().equals("=") == equal;
    }
    if (typeLeft >= 5) {
      return UNDEFINED;
    }
    int comparison = compareSameType(left, right);
    return switch (binary.operator()) {
      case "<" -> comparison < 0;
      case "<=" -> comparison <= 0;
      case ">" -> comparison > 0;
      case ">=" -> comparison >= 0;
      default -> throw new UnsupportedQueryException("Unsupported operator " + binary.operator());
    };
  }

  /** undefined, null, boolean, number, string, array, object: the {@code ORDER BY} type order. */
  private static int typeRank(Object value) {
    if (value == UNDEFINED) {
      return 0;
    }
    if (value == null) {
      return 1;
    }
    if (value instanceof Boolean) {
      return 2;
    }
    if (value instanceof Number) {
      return 3;
    }
    if (value instanceof String) {
      return 4;
    }
    if (value instanceof List<?>) {
      return 5;
    }
    return 6;
  }

  private static int compareSameType(Object left, Object right) {
    if (left == null) {
      return 0;
    }
    if (left instanceof Boolean leftBoolean) {
      return Boolean.compare(leftBoolean, (Boolean) right);
    }
    if (left instanceof Number leftNumber) {
      return Double.compare(leftNumber.doubleValue(), ((Number) right).doubleValue());
    }
    return ((String) left).compareTo((String) right);
  }

  private static int compareForOrder(Object left, Object right) {
    int rank = Integer.compare(typeRank(left), typeRank(right));
    if (rank != 0 || typeRank(left) >= 5 || typeRank(left) == 0) {
      return rank;
    }
    return compareSameType(left, right);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Query plans

  /**
   * The "partitioned query execution info" the gateway returns for a query-plan request: what the client has to do
   * itself, and the query to send to every partition key range ({@code rewrittenQuery}). As the gateway does, an
   * {@code ORDER BY} query is rewritten to return {@code {_rid, orderByItems: [{item}], payload}} rows filtered by the
   * {@value #ORDER_BY_FILTER} placeholder, and {@code OFFSET}/{@code LIMIT} are left to the client.
   *
   * @param query the parsed query
   * @return the plan as raw JSON
   */
  static Map<String, Object> plan(Query query) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("distinctType", query.distinct() ? (query.orderBy().isEmpty() ? "Unordered" : "Ordered") : "None");
    info.put("top", query.top());
    info.put("offset", query.offset());
    info.put("limit", query.limit());
    List<String> orderBy = new ArrayList<>();
    List<String> orderByExpressions = new ArrayList<>();
    for (OrderItem item : query.orderBy()) {
      orderBy.add(item.descending() ? "Descending" : "Ascending");
      orderByExpressions.add(item.path().text());
    }
    info.put("orderBy", orderBy);
    info.put("orderByExpressions", orderByExpressions);
    info.put("groupByExpressions", List.of());
    info.put("groupByAliases", List.of());
    info.put("aggregates", query.isAggregate() ? List.of("Count") : List.of());
    info.put("groupByAliasToAggregateType", Map.of());
    info.put("rewrittenQuery", query.orderBy().isEmpty() ? "" : rewrittenOrderByQuery(query));
    info.put("hasSelectValue", query.value());
    info.put("dCountInfo", null);
    Map<String, Object> range = new LinkedHashMap<>();
    range.put("min", "");
    range.put("max", "FF");
    range.put("isMinInclusive", true);
    range.put("isMaxInclusive", false);
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("partitionedQueryExecutionInfoVersion", 2);
    plan.put("queryInfo", info);
    plan.put("queryRanges", List.of(range));
    return plan;
  }

  private static String rewrittenOrderByQuery(Query query) {
    if (!query.star() || query.distinct() || query.value() || query.isAggregate()) {
      throw new UnsupportedQueryException("Only SELECT * queries can be ordered across partitions by the fake");
    }
    StringBuilder items = new StringBuilder();
    StringBuilder order = new StringBuilder();
    for (OrderItem item : query.orderBy()) {
      if (!items.isEmpty()) {
        items.append(", ");
        order.append(", ");
      }
      items.append("{\"item\": ").append(item.path().text()).append("}");
      order.append(item.text());
    }
    String top = query.top() == null ? "" : "TOP " + query.top() + " ";
    String filter = "(" + ORDER_BY_FILTER + ")" + (query.whereText() == null ? "" : " AND (" + query.whereText() + ")");
    return "SELECT " + top + "c._rid, [" + items + "] AS orderByItems, c AS payload FROM c WHERE " + filter
        + " ORDER BY " + order;
  }
}
