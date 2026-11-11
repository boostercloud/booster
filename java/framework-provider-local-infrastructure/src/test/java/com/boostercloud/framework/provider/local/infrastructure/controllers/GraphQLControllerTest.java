package com.boostercloud.framework.provider.local.infrastructure.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.LocalHttpRequest;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.types.errors.NotFoundError;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/controllers/graphql.test.ts} ("GraphQL controller"). */
class GraphQLControllerTest {

  /** An error whose class is named like JavaScript's plain {@code Error}, the error the TypeScript test throws. */
  static final class Error extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Error(String message) {
      super(message);
    }
  }

  private Object mockQueryResponse;
  private GraphQLService graphQLServiceStub;
  private NextFunction nextStub;
  private GraphQLController sut;

  @BeforeEach
  void setUp() {
    mockQueryResponse = ApiAdapter.requestSucceeded(Map.of("data", Map.of("ChangeCartItem", true)), Map.of());
    graphQLServiceStub = mock(GraphQLService.class);
    when(graphQLServiceStub.handleGraphQLRequest(any())).thenReturn(mockQueryResponse);
    nextStub = mock(NextFunction.class);
    sut = new GraphQLController(graphQLServiceStub);
  }

  @Nested
  class HandleGraphQL {
    private HttpRequest mockRequest;
    private MemoryHttpResponse mockResponse;

    @BeforeEach
    void setUp() {
      mockRequest = HttpRequest.builder("POST", "/").header("authorization", "Bearer token").build();
      mockRequest.body(Map.of("query", "mutation { ChangeCartItem }"));
      mockResponse = spy(new MemoryHttpResponse());
    }

    @Test
    void shouldCallGraphQLServiceHandleGraphQLRequest() {
      sut.handleGraphQL(mockRequest, mockResponse, nextStub);

      verify(graphQLServiceStub, times(1)).handleGraphQLRequest(mockRequest.toLocalHttpRequest());
    }

    @Test
    void passesTheMethodMountRelativeUrlHeadersAndBodyToTheService() {
      sut.handleGraphQL(mockRequest, mockResponse, nextStub);

      verify(graphQLServiceStub).handleGraphQLRequest(new LocalHttpRequest("POST", "/",
          Map.of("authorization", "Bearer token"), Map.of("query", "mutation { ChangeCartItem }"), null));
    }

    @Nested
    class OnSuccess {
      @BeforeEach
      void setUp() {
        sut.handleGraphQL(mockRequest, mockResponse, nextStub);
      }

      @Test
      void shouldNotCallNext() {
        verify(nextStub, never()).next(any());
        verify(nextStub, never()).next();
      }

      @Test
      void shouldReturnExpectedStatusCode() {
        verify(mockResponse, times(1)).status(200);
        assertThat(mockResponse.sentStatus()).isEqualTo(200);
      }

      @Test
      void shouldCallResponseJsonWithExpectedArguments() {
        verify(mockResponse, times(1)).json(Map.of("data", Map.of("ChangeCartItem", true)));
        assertThat(mockResponse.bodyAsJson()).isEqualTo(Map.of("data", Map.of("ChangeCartItem", true)));
        assertThat(mockResponse.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
      }

      @Test
      void doesNotSendTheHeadersOfTheResult() {
        assertThat(mockResponse.headers()).containsOnlyKeys("Content-Type", "Content-Length");
      }
    }

    @Nested
    class OnFailure {
      private RuntimeException error;

      @BeforeEach
      void setUp() {
        error = new Error("lorem ipsum dolor");
        when(graphQLServiceStub.handleGraphQLRequest(any())).thenThrow(error);
        sut.handleGraphQL(mockRequest, mockResponse, nextStub);
      }

      @Test
      void shouldReturnExpectedStatusCode() {
        verify(mockResponse, times(1)).status(500);
      }

      @Test
      void shouldCallResponseJsonWithExpectedArguments() {
        verify(mockResponse, times(1)).json(Map.of("title", "Error", "reason", error.getMessage()));
      }

      @Test
      void shouldCallNext() {
        verify(nextStub, times(1)).next(error);
      }
    }
  }

  @Test
  void answersABoosterErrorWithItsStatusAndTitle() {
    when(graphQLServiceStub.handleGraphQLRequest(any())).thenThrow(new NotFoundError("Nothing here"));
    MemoryHttpResponse response = new MemoryHttpResponse();

    sut.handleGraphQL(HttpRequest.builder("POST", "/").build(), response, nextStub);

    assertThat(response.sentStatus()).isEqualTo(404);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Not Found Error", "reason", "Nothing here"));
  }

  @Test
  void javaExceptionsStandingForPlainErrorsKeepTheirClassTitle() {
    when(graphQLServiceStub.handleGraphQLRequest(any())).thenThrow(new IllegalStateException("boom"));
    MemoryHttpResponse response = new MemoryHttpResponse();

    sut.handleGraphQL(HttpRequest.builder("POST", "/").build(), response, nextStub);

    assertThat(response.sentStatus()).isEqualTo(500);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Illegal State Exception", "reason", "boom"));
  }

  @Test
  void aMissingResultFailsLikeReadingAPropertyOfUndefined() {
    when(graphQLServiceStub.handleGraphQLRequest(any())).thenReturn(null);
    MemoryHttpResponse response = new MemoryHttpResponse();

    sut.handleGraphQL(HttpRequest.builder("POST", "/").build(), response, nextStub);

    assertThat(response.sentStatus()).isEqualTo(500);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Illegal State Exception",
        "reason", "Cannot read properties of undefined (reading 'result')"));
  }

  @Test
  void aResultWithoutBodyIsAnEmptyJsonResponse() {
    when(graphQLServiceStub.handleGraphQLRequest(any())).thenReturn(new ApiAdapter.Failure(500, "Error", "x"));
    MemoryHttpResponse response = new MemoryHttpResponse();

    sut.handleGraphQL(HttpRequest.builder("POST", "/").build(), response, nextStub);

    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(response.bodyAsString()).isEmpty();
    assertThat(response.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
  }

  @Test
  void mapResultsAreReadLikeObjects() {
    when(graphQLServiceStub.handleGraphQLRequest(any()))
        .thenReturn(Map.of("status", "success", "result", Map.of("data", 1)));
    MemoryHttpResponse response = new MemoryHttpResponse();

    sut.handleGraphQL(HttpRequest.builder("POST", "/").build(), response, nextStub);

    assertThat(response.bodyAsJson()).isEqualTo(Map.of("data", 1));
  }

  @Test
  void theRouterServesPostOnItsRoot() {
    assertThat(sut.router().size()).isEqualTo(1);
    MemoryHttpResponse response = new MemoryHttpResponse();
    NextFunction out = mock(NextFunction.class);

    sut.router().handle(HttpRequest.builder("POST", "/?x=1").build(), response, out);

    assertThat(response.sentStatus()).isEqualTo(200);
    verify(out, never()).next(any());
  }

  @Test
  void theRouterIgnoresOtherMethods() {
    MemoryHttpResponse response = new MemoryHttpResponse();
    NextFunction out = mock(NextFunction.class);

    sut.router().handle(HttpRequest.builder("GET", "/").build(), response, out);

    verify(out).next(null);
    verify(graphQLServiceStub, never()).handleGraphQLRequest(any());
  }
}
