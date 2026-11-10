package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.errors.BoosterError;
import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.InvalidSyntaxException;
import graphql.schema.GraphQLSchema;
import graphql.validation.OperationValidationRule;
import graphql.validation.QueryComplexityLimits;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Runs GraphQL documents against the Booster schema the way graphql-js does for TypeScript, and turns results and
 * errors into the JSON shapes graphql-js produces ({@code GraphQLError#toJSON}). Java addition: graphql-js offers
 * {@code execute}/{@code subscribe} on an already parsed and validated document, and its error objects serialise
 * themselves.
 *
 * <ul>
 *   <li>A resolver failure becomes an error whose message is the thrown error's message (graphql-java's default
 *       handler would write {@code Exception while fetching data (/path) : ...}), with the field location and path,
 *       keeping the thrown error as its original error.</li>
 *   <li>{@link #validate(GraphQLSchema, Document) validate} applies the GraphQL specification's rules only, like
 *       graphql-js {@code validate(schema, document)}: graphql-java's own additions, the "good faith introspection"
 *       rule (which rejects, for instance, a {@code __Type.inputFields} selected inside another one) and the query
 *       complexity limits, are left out.</li>
 *   <li>{@link #formatError(Object, boolean) formatError(error, true)} is the dispatcher's
 *       {@code toGraphQLErrorWithExtensions}: {@code { message, locations?, path?, extensions: { code?, data? } }}
 *       where {@code code}/{@code data} come from the original {@link BoosterError} (the error class name by default),
 *       and the empty {@code extensions: {}} for any other error.</li>
 * </ul>
 */
public final class GraphQLExecution {

  private static final DataFetcherExceptionHandler EXCEPTION_HANDLER = new BoosterDataFetcherExceptionHandler();

  private GraphQLExecution() {}

  /**
   * Executes (or, for a subscription operation, subscribes to) a document that has already been parsed and validated
   * (graphql-js {@code execute}/{@code subscribe} with {@code { schema, document, contextValue, variableValues,
   * operationName }}).
   *
   * @param schema the schema
   * @param document the parsed document
   * @param query the text of the document
   * @param context the resolver context
   * @param variables the variables, or {@code null}
   * @param operationName the operation to run, or {@code null}
   * @return the result; for subscriptions its data is the publisher of results (or {@code null} with errors when the
   *     subscription could not be set up), which emits them in the order of the source events (graphql-js yields them
   *     in order; graphql-java needs {@code KEEP_SUBSCRIPTION_EVENTS_ORDERED})
   */
  public static ExecutionResult execute(GraphQLSchema schema, Document document, String query,
      GraphQLResolverContext context, Map<String, Object> variables, String operationName) {
    GraphQL graphQL = GraphQL.newGraphQL(schema)
        .defaultDataFetcherExceptionHandler(EXCEPTION_HANDLER)
        .preparsedDocumentProvider((executionInput, parseAndValidate) ->
            CompletableFuture.completedFuture(new PreparsedDocumentEntry(document)))
        .build();
    Map<Object, Object> graphQLContext = Common.graphQLContextOf(context);
    graphQLContext.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true);
    ExecutionInput.Builder input = ExecutionInput.newExecutionInput()
        .query(query == null ? "" : query)
        .operationName(operationName)
        .graphQLContext(graphQLContext)
        .localContext(context);
    if (variables != null) {
      input.variables(variables);
    }
    return graphQL.execute(input.build());
  }

  /**
   * graphql-js {@code validate(schema, document)}: the rules of the GraphQL specification ({@code specifiedRules}),
   * with the messages in English. graphql-java also checks introspection queries in "good faith" (each of
   * {@code __Type.fields}, {@code inputFields}, {@code interfaces}, {@code possibleTypes} at most once, limited depth
   * and size) and applies query complexity limits; graphql-js has neither, so neither is applied.
   *
   * @param schema the schema
   * @param document the parsed document
   * @return the validation errors, empty when the document is valid
   */
  public static List<ValidationError> validate(GraphQLSchema schema, Document document) {
    return new Validator().validateDocument(schema, document,
        rule -> rule != OperationValidationRule.GOOD_FAITH_INTROSPECTION, Locale.ENGLISH, QueryComplexityLimits.NONE);
  }

  /**
   * @param result an execution result
   * @return its JSON form: {@code errors} (formatted with {@code extensions}) when there are errors, then {@code data}
   *     when execution started
   */
  public static Map<String, Object> toResultMap(ExecutionResult result) {
    return toResultMap(result, true);
  }

  /**
   * @param result an execution result
   * @param withExtensions whether the errors get the Booster {@code extensions} ({@code toGraphQLErrorWithExtensions})
   * @return its JSON form ({@code errors} first when present, then {@code data})
   */
  public static Map<String, Object> toResultMap(ExecutionResult result, boolean withExtensions) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (result.getErrors() != null && !result.getErrors().isEmpty()) {
      map.put("errors", formatErrors(result.getErrors(), withExtensions));
    }
    if (result.isDataPresent()) {
      map.put("data", result.getData());
    }
    return map;
  }

  /**
   * @param errors GraphQL errors or other throwables
   * @param withExtensions whether the Booster {@code extensions} are added
   * @return their JSON forms, in order
   */
  public static List<Map<String, Object>> formatErrors(List<?> errors, boolean withExtensions) {
    List<Map<String, Object>> formatted = new ArrayList<>();
    for (Object error : errors) {
      formatted.add(formatError(error, withExtensions));
    }
    return formatted;
  }

  /**
   * The JSON form of an error ({@code toGraphQLErrorWithExtensions} followed by graphql-js {@code toJSON} when
   * {@code withExtensions}, plain {@code toJSON} otherwise).
   *
   * @param error a {@link GraphQLError} or a {@link Throwable}
   * @param withExtensions whether the Booster {@code extensions} ({@code code}, {@code data}) are added
   * @return {@code { message, locations?, path?, extensions? }}
   */
  public static Map<String, Object> formatError(Object error, boolean withExtensions) {
    Object normalized = error instanceof InvalidSyntaxException syntax ? syntax.toInvalidSyntaxError() : error;
    Map<String, Object> formatted = new LinkedHashMap<>();
    Throwable originalError;
    if (normalized instanceof GraphQLError graphQLError) {
      formatted.put("message", nonNullMessage(graphQLError.getMessage()));
      List<SourceLocation> locations = graphQLError.getLocations();
      if (locations != null && !locations.isEmpty()) {
        formatted.put("locations", locations.stream().map(GraphQLExecution::location).toList());
      }
      List<Object> path = graphQLError.getPath();
      if (path != null && !path.isEmpty()) {
        formatted.put("path", path);
      }
      originalError = originalErrorOf(graphQLError);
    } else if (normalized instanceof Throwable throwable) {
      formatted.put("message", nonNullMessage(throwable.getMessage()));
      originalError = throwable;
    } else {
      formatted.put("message", String.valueOf(normalized));
      originalError = null;
    }
    if (withExtensions) {
      Map<String, Object> extensions = new LinkedHashMap<>();
      if (originalError instanceof BoosterError boosterError) {
        if (boosterError.code() != null) {
          extensions.put("code", boosterError.code());
        }
        if (boosterError.data() != null) {
          extensions.put("data", boosterError.data());
        }
      }
      formatted.put("extensions", extensions);
    }
    return formatted;
  }

  /**
   * @param error a GraphQL error
   * @return the error a resolver threw for it, or {@code null}
   */
  public static Throwable originalErrorOf(GraphQLError error) {
    return switch (error) {
      case LocatedError located -> located.originalError();
      case graphql.ExceptionWhileDataFetching fetching -> fetching.getException();
      case Throwable throwable -> throwable.getCause();
      default -> null;
    };
  }

  private static Map<String, Object> location(SourceLocation location) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("line", location.getLine());
    map.put("column", location.getColumn());
    return map;
  }

  private static String nonNullMessage(String message) {
    return message == null ? "" : message;
  }

  /**
   * @param error a throwable
   * @return the error wrapped by {@link CompletionException}, {@link ExecutionException},
   *     {@link InvocationTargetException} or {@link UndeclaredThrowableException}
   */
  static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException
        || current instanceof InvocationTargetException || current instanceof UndeclaredThrowableException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * A resolver error located in the document (graphql-js {@code locatedError}): the message of the original error,
   * the location of the field and the path of the value.
   *
   * @param message the message
   * @param locations the field locations
   * @param path the result path
   * @param originalError the error the resolver threw
   */
  public record LocatedError(String message, List<SourceLocation> locations, List<Object> path,
      Throwable originalError) implements GraphQLError {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
      return locations;
    }

    @Override
    public ErrorClassification getErrorType() {
      return ErrorType.DataFetchingException;
    }

    @Override
    public List<Object> getPath() {
      return path;
    }
  }

  private static final class BoosterDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
        DataFetcherExceptionHandlerParameters handlerParameters) {
      Throwable exception = unwrap(handlerParameters.getException());
      SourceLocation sourceLocation = handlerParameters.getSourceLocation();
      List<Object> path = handlerParameters.getPath() == null ? List.of() : handlerParameters.getPath().toList();
      GraphQLError error = exception instanceof GraphQLError graphQLError && graphQLError.getPath() != null
          ? graphQLError
          : new LocatedError(nonNullMessage(exception.getMessage()),
              sourceLocation == null ? List.of() : List.of(sourceLocation), path, exception);
      return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }
  }
}
