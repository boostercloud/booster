package com.boostercloud.application.tester.graphql;

import graphql.language.ArrayValue;
import graphql.language.AstPrinter;
import graphql.language.BooleanValue;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.EnumValue;
import graphql.language.Field;
import graphql.language.FloatValue;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.IntValue;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A parsed GraphQL document, with the document utilities of Apollo Client the test client relies on: the
 * {@code gql} tag ({@link #parse(String)}), {@code checkDocument}, {@code getOperationName},
 * {@code getMainDefinition}, {@code getDefaultValues} and the {@code addTypenameToDocument} transform of
 * {@code InMemoryCache}. Parsing and printing use graphql-java.
 */
public final class GraphQLDocument {

  /** The field Apollo adds to selection sets. */
  public static final String TYPENAME_FIELD = "__typename";

  private final Document document;

  private GraphQLDocument(Document document) {
    this.document = document;
  }

  /**
   * Parses and checks a document ({@code gql} + {@code checkDocument}).
   *
   * @param source the GraphQL text
   * @return the document
   * @throws IllegalArgumentException for syntax errors, for type-system definitions
   *     ({@code Schema type definitions not allowed in queries. Found: "<kind>"}) and for documents with several
   *     operations ({@code Ambiguous GraphQL document: contains <n> operations})
   */
  public static GraphQLDocument parse(String source) {
    Objects.requireNonNull(source, "source");
    Document document;
    try {
      document = Parser.parse(source);
    } catch (InvalidSyntaxException e) {
      throw new IllegalArgumentException("Syntax Error: " + e.getMessage(), e);
    }
    return of(document);
  }

  /**
   * @param document a graphql-java document
   * @return the checked document
   * @throws IllegalArgumentException see {@link #parse(String)}
   */
  public static GraphQLDocument of(Document document) {
    Objects.requireNonNull(document, "document");
    int operations = 0;
    for (Definition<?> definition : document.getDefinitions()) {
      if (definition instanceof FragmentDefinition) {
        continue;
      }
      if (!(definition instanceof OperationDefinition)) {
        throw new IllegalArgumentException(
            "Schema type definitions not allowed in queries. Found: \"" + kind(definition) + "\"");
      }
      operations++;
    }
    if (operations > 1) {
      throw new IllegalArgumentException("Ambiguous GraphQL document: contains " + operations + " operations");
    }
    return new GraphQLDocument(document);
  }

  /**
   * @return the graphql-java AST
   */
  public Document ast() {
    return document;
  }

  /**
   * @return the document printed by graphql-java (what is sent to the server)
   */
  public String print() {
    return AstPrinter.printAst(document);
  }

  /**
   * {@code getOperationName}: the name of the first named operation.
   *
   * @return the operation name, empty for anonymous operations and fragment-only documents
   */
  public Optional<String> operationName() {
    return document.getDefinitionsOfType(OperationDefinition.class).stream()
        .map(OperationDefinition::getName)
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * {@code getOperationDefinition}: the first operation.
   *
   * @return the operation, empty for fragment-only documents
   */
  public Optional<OperationDefinition> operationDefinition() {
    return document.getDefinitionsOfType(OperationDefinition.class).stream().findFirst();
  }

  /**
   * {@code getMainDefinition}: the first query/mutation/subscription, else the first fragment.
   *
   * @return the main definition
   * @throws IllegalArgumentException
   *     {@code Expected a parsed GraphQL query with a query, mutation, subscription, or a fragment.}
   */
  public Definition<?> mainDefinition() {
    FragmentDefinition fragment = null;
    for (Definition<?> definition : document.getDefinitions()) {
      if (definition instanceof OperationDefinition operation) {
        return operation;
      }
      if (definition instanceof FragmentDefinition fragmentDefinition && fragment == null) {
        fragment = fragmentDefinition;
      }
    }
    if (fragment != null) {
      return fragment;
    }
    throw new IllegalArgumentException(
        "Expected a parsed GraphQL query with a query, mutation, subscription, or a fragment.");
  }

  /**
   * The test of Apollo's split link: whether the main definition is a subscription operation.
   *
   * @return {@code true} for subscriptions
   */
  public boolean isSubscription() {
    return mainDefinition() instanceof OperationDefinition operation
        && operation.getOperation() == OperationDefinition.Operation.SUBSCRIPTION;
  }

  /**
   * {@code getDefaultValues(getOperationDefinition(doc))}: the default values of the operation's variables, as raw
   * values (integers as {@code Integer}/{@code Long}, floats as {@code Double}, enums as strings).
   *
   * @return the defaults, in declaration order
   */
  public Map<String, Object> defaultVariableValues() {
    Map<String, Object> defaults = new LinkedHashMap<>();
    operationDefinition().ifPresent(operation -> {
      for (VariableDefinition variable : operation.getVariableDefinitions()) {
        if (variable.getDefaultValue() != null) {
          defaults.put(variable.getName(), rawValue(variable.getDefaultValue(), Map.of()));
        }
      }
    });
    return defaults;
  }

  /**
   * The variables the document references outside variable definitions (what Apollo's {@code HttpLink} keeps when
   * it strips unused variables).
   *
   * @return the referenced variable names
   */
  public Set<String> referencedVariables() {
    Set<String> names = new LinkedHashSet<>();
    collectVariables(document, names);
    return Collections.unmodifiableSet(names);
  }

  /**
   * {@code addTypenameToDocument}: appends a {@code __typename} field to every selection set except the root
   * selection sets of operations, unless the set already selects {@code __typename} or any {@code __}-prefixed field
   * (introspection), or belongs to a field with an {@code @export} directive.
   *
   * @return the transformed document
   */
  @SuppressWarnings("rawtypes")
  public GraphQLDocument withTypename() {
    List<Definition> definitions = new ArrayList<>();
    for (Definition<?> definition : document.getDefinitions()) {
      definitions.add(switch (definition) {
        case OperationDefinition operation -> operation.transform(
            builder -> builder.selectionSet(addTypename(operation.getSelectionSet(), false, null)));
        case FragmentDefinition fragment -> fragment.transform(
            builder -> builder.selectionSet(addTypename(fragment.getSelectionSet(), true, null)));
        default -> definition;
      });
    }
    return new GraphQLDocument(document.transform(builder -> builder.definitions(definitions)));
  }

  @Override
  public String toString() {
    return print();
  }

  @SuppressWarnings("rawtypes")
  private static SelectionSet addTypename(SelectionSet set, boolean addHere, Field parentField) {
    if (set == null) {
      return null;
    }
    List<Selection> selections = new ArrayList<>();
    for (Selection<?> selection : set.getSelections()) {
      selections.add(switch (selection) {
        case Field field when field.getSelectionSet() != null -> field.transform(
            builder -> builder.selectionSet(addTypename(field.getSelectionSet(), true, field)));
        case InlineFragment fragment -> fragment.transform(
            builder -> builder.selectionSet(addTypename(fragment.getSelectionSet(), true, null)));
        default -> selection;
      });
    }
    if (addHere && needsTypename(set, parentField)) {
      selections.add(Field.newField(TYPENAME_FIELD).build());
    }
    return set.transform(builder -> builder.selections(selections));
  }

  private static boolean needsTypename(SelectionSet set, Field parentField) {
    boolean skip = set.getSelections().stream()
        .anyMatch(selection -> selection instanceof Field field && field.getName().startsWith("__"));
    if (skip) {
      return false;
    }
    return parentField == null || parentField.getDirectives().stream().noneMatch(d -> d.getName().equals("export"));
  }

  private static void collectVariables(Node<?> node, Set<String> names) {
    if (node instanceof VariableReference reference) {
      names.add(reference.getName());
    }
    for (Node<?> child : node.getChildren()) {
      collectVariables(child, names);
    }
  }

  /** {@code valueToObjectRepresentation}. */
  static Object rawValue(Value<?> value, Map<String, ?> variables) {
    return switch (value) {
      case IntValue intValue -> number(intValue.getValue());
      case FloatValue floatValue -> floatValue.getValue().doubleValue();
      case StringValue stringValue -> stringValue.getValue();
      case BooleanValue booleanValue -> booleanValue.isValue();
      case NullValue ignored -> null;
      case EnumValue enumValue -> enumValue.getName();
      case VariableReference reference -> variables.get(reference.getName());
      case ArrayValue arrayValue -> {
        List<Object> list = new ArrayList<>();
        arrayValue.getValues().forEach(item -> list.add(rawValue(item, variables)));
        yield list;
      }
      case ObjectValue objectValue -> {
        Map<String, Object> map = new LinkedHashMap<>();
        for (ObjectField field : objectValue.getObjectFields()) {
          map.put(field.getName(), rawValue(field.getValue(), variables));
        }
        yield map;
      }
      default -> throw new IllegalArgumentException(
          "The inline argument \"" + value + "\" of kind \"" + value.getClass().getSimpleName()
              + "\" is not supported. Use variables instead of inline arguments to overcome this limitation.");
    };
  }

  private static Object number(BigInteger value) {
    if (value.bitLength() < 32) {
      return value.intValue();
    }
    if (value.bitLength() < 64) {
      return value.longValue();
    }
    return value.doubleValue();
  }

  /** The graphql-js {@code kind} of a definition (graphql-java names type extensions {@code ...ExtensionDefinition}). */
  private static String kind(Definition<?> definition) {
    String name = definition.getClass().getSimpleName();
    return name.endsWith("ExtensionDefinition") ? name.substring(0, name.length() - "Definition".length()) : name;
  }
}
