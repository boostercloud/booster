package com.boostercloud.application.tester.graphql;

import java.util.List;
import java.util.Optional;

/**
 * A failed GraphQL operation: Apollo Client's {@code ApolloError}. It carries either the GraphQL errors of the
 * response ({@link #graphQLErrors()}) or the transport failure ({@link #networkError()}: {@link ServerError},
 * {@link ServerParseError}, {@link FetchError}, ...).
 *
 * <p>The message follows Apollo's {@code generateErrorMessage}: the message of every GraphQL error followed by the
 * network error's message, joined with {@code "\n"} (so it equals the error message when there is a single
 * error); an error without message contributes {@code "undefined"}.
 *
 * <p>Subscription observers receive this exception when a result carries errors (Apollo throws an
 * {@code ApolloError} from the result mapper) and when the server sends an {@code error} message for the
 * operation; in the latter case the message is the <em>first</em> error's message, which is what the TypeScript
 * observer receives ({@code subscriptions-transport-ws} passes {@code errors[0]}).
 */
public class GraphQLClientException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The GraphQL errors. */
  private final transient List<GraphQLError> graphQLErrors;

  /** The transport failure, if any. */
  private final Throwable networkError;

  /**
   * @param graphQLErrors the GraphQL errors (may be empty)
   * @param networkError the transport failure, or {@code null}
   * @param errorMessage an explicit message, or {@code null}/empty to generate Apollo's
   */
  public GraphQLClientException(List<GraphQLError> graphQLErrors, Throwable networkError, String errorMessage) {
    super(
        errorMessage != null && !errorMessage.isEmpty()
            ? errorMessage
            : generateErrorMessage(graphQLErrors == null ? List.of() : graphQLErrors, networkError),
        networkError);
    this.graphQLErrors = graphQLErrors == null ? List.of() : List.copyOf(graphQLErrors);
    this.networkError = networkError;
  }

  /**
   * @param graphQLErrors the errors of a response
   * @return {@code new ApolloError({ graphQLErrors })}
   */
  public static GraphQLClientException ofGraphQLErrors(List<GraphQLError> graphQLErrors) {
    return new GraphQLClientException(graphQLErrors, null, null);
  }

  /**
   * @param networkError the transport failure
   * @return {@code new ApolloError({ networkError })}
   */
  public static GraphQLClientException ofNetworkError(Throwable networkError) {
    return new GraphQLClientException(List.of(), networkError, null);
  }

  /**
   * The error a subscription observer receives for an {@code error} message of the server.
   *
   * @param errors the formatted errors of the message (never empty)
   * @return an exception whose message is the first error's message
   */
  static GraphQLClientException ofOperationErrors(List<GraphQLError> errors) {
    String message = errors.isEmpty() ? null : errors.getFirst().message();
    return new GraphQLClientException(errors, null, message == null ? "undefined" : message);
  }

  /**
   * @return the GraphQL errors exactly as the server sent them (Apollo's {@code graphQLErrors})
   */
  public List<GraphQLError> graphQLErrors() {
    return graphQLErrors;
  }

  /**
   * @return the transport failure (Apollo's {@code networkError}), empty for GraphQL errors
   */
  public Optional<Throwable> networkError() {
    return Optional.ofNullable(networkError);
  }

  private static String generateErrorMessage(List<GraphQLError> graphQLErrors, Throwable networkError) {
    StringBuilder message = new StringBuilder();
    for (GraphQLError error : graphQLErrors) {
      String errorMessage = error == null ? "Error message not found." : error.message();
      message.append(errorMessage == null ? "undefined" : errorMessage).append('\n');
    }
    if (networkError != null) {
      message.append(networkError.getMessage() == null ? networkError.toString() : networkError.getMessage())
          .append('\n');
    }
    if (!message.isEmpty() && message.charAt(message.length() - 1) == '\n') {
      message.setLength(message.length() - 1);
    }
    return message.toString();
  }
}
