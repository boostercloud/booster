package com.boostercloud.framework.provider.local.infrastructure.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.LocalHttpRequest;
import com.boostercloud.framework.provider.local.services.HealthService;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code controllers/health-controller.ts} (no TypeScript test exists). */
class HealthControllerTest {

  private HealthService healthService;
  private NextFunction next;
  private HealthController sut;
  private MemoryHttpResponse response;

  @BeforeEach
  void setUp() {
    healthService = mock(HealthService.class);
    next = mock(NextFunction.class);
    sut = new HealthController(healthService);
    response = new MemoryHttpResponse();
  }

  @Test
  void answersSuccessesWith200AndTheResult() {
    List<Map<String, Object>> results = List.of(Map.of("id", "booster", "status", "UP"));
    when(healthService.handleHealthRequest(any())).thenReturn(ApiAdapter.healthRequestResult(results, true));

    sut.handleHealth(HttpRequest.builder("GET", "/booster").build(), response, next);

    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(response.bodyAsJson()).isEqualTo(results);
    verify(next, never()).next(any());
  }

  @Test
  void answersUnhealthyResultsWith200TooBecauseTheStatusCodeHeaderIsIgnored() {
    when(healthService.handleHealthRequest(any()))
        .thenReturn(ApiAdapter.healthRequestResult(List.of(Map.of("status", "DOWN")), false));

    sut.handleHealth(HttpRequest.builder("GET", "/").build(), response, next);

    assertThat(response.sentStatus()).isEqualTo(200);
    assertThat(response.getHeader("status-code")).isNull();
  }

  @Test
  void answersFailuresWithTheirCodeAndOnlyTheTitle() {
    when(healthService.handleHealthRequest(any()))
        .thenReturn(ApiAdapter.requestFailed(new NotAuthorizedError("You can't")));

    sut.handleHealth(HttpRequest.builder("GET", "/").build(), response, next);

    assertThat(response.sentStatus()).isEqualTo(401);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Not Authorized Error"));
  }

  @Test
  void readsTheMessageOfMapFailures() {
    when(healthService.handleHealthRequest(any()))
        .thenReturn(Map.of("status", "failure", "code", 403, "title", "Forbidden", "message", "no"));

    sut.handleHealth(HttpRequest.builder("GET", "/").build(), response, next);

    assertThat(response.sentStatus()).isEqualTo(403);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Forbidden", "reason", "no"));
  }

  @Test
  void passesTheComponentPathRelativeToTheMountPoint() {
    when(healthService.handleHealthRequest(any())).thenReturn(ApiAdapter.healthRequestResult(List.of(), true));
    HttpRequest request = HttpRequest.builder("GET", "/booster/database").header("Authorization", "Bearer x").build();

    sut.handleHealth(request, response, next);

    verify(healthService).handleHealthRequest(
        new LocalHttpRequest("GET", "/booster/database", Map.of("authorization", "Bearer x"), null, null));
  }

  @Test
  void answersThrownErrorsAndCallsNext() {
    RuntimeException error = new IllegalStateException("broken");
    when(healthService.handleHealthRequest(any())).thenThrow(error);

    sut.handleHealth(HttpRequest.builder("GET", "/").build(), response, next);

    assertThat(response.sentStatus()).isEqualTo(500);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Illegal State Exception", "reason", "broken"));
    verify(next).next(error);
  }

  @Test
  void theRouteMatchesEveryPathUnderTheMountPoint() {
    when(healthService.handleHealthRequest(any())).thenReturn(ApiAdapter.healthRequestResult(List.of(), true));
    for (String url : List.of("/", "/booster", "/booster/database/events", "/rockets/x?y=1")) {
      MemoryHttpResponse each = new MemoryHttpResponse();
      sut.router().handle(HttpRequest.builder("GET", url).build(), each, next);
      assertThat(each.sentStatus()).as(url).isEqualTo(200);
    }
    MemoryHttpResponse head = new MemoryHttpResponse(true);
    sut.router().handle(HttpRequest.builder("HEAD", "/").build(), head, next);
    assertThat(head.sentStatus()).isEqualTo(200);
    assertThat(head.bodyBytes()).isEmpty();
  }
}
