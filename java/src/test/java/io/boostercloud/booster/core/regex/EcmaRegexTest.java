package io.boostercloud.booster.core.regex;

import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EcmaRegexTest {
  @Test void matchesRecordedEcmascriptOracleIncludingLookaroundBackreferencesAndUnicode() throws Exception {
    try (var input = getClass().getResourceAsStream("/compatibility/ecmascript-regex.json")) {
      assertNotNull(input);
      var document = Json.MAPPER.readTree(input); assertEquals(40, document.path("fixtures").size());
      for (var fixture : document.path("fixtures")) {
        String name = fixture.path("name").asText();
        if (fixture.path("valid").asBoolean()) assertEquals(fixture.path("matched").asBoolean(),
            EcmaRegex.matches(fixture.path("input").asText(), fixture.path("pattern").asText(), fixture.path("ignoreCase").asBoolean()), name);
        else assertEquals(BoosterException.Code.VALIDATION, assertThrows(BoosterException.class,
            () -> EcmaRegex.matches(fixture.path("input").asText(), fixture.path("pattern").asText(), fixture.path("ignoreCase").asBoolean()), name).code());
      }
    }
  }
  @Test @Timeout(10) void cancelsCatastrophicBacktrackingAndRecoversForTheNextRequest() {
    assertTrue(EcmaRegex.matches("warm", "warm", false));
    assertTimeout(Duration.ofSeconds(3), () -> {
      var error = assertThrows(BoosterException.class, () -> EcmaRegex.matches("a".repeat(10000) + "!", "^(a+)+\\1$", false));
      assertEquals(BoosterException.Code.VALIDATION, error.code()); assertTrue(error.getMessage().contains("budget"));
    });
    assertTrue(EcmaRegex.matches("hello hello", "(?<word>\\w+)\\s+\\k<word>", false));
  }
  @Test @Timeout(10) void concurrentExpensiveInputsStayBoundedAndDoNotLeakMatcherThreads() throws Exception {
    assertTrue(EcmaRegex.matches("warm", "warm", false));
    try (var requests = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 12; i++) futures.add(requests.submit(() -> {
        var error = assertThrows(BoosterException.class, () -> EcmaRegex.matches("a".repeat(10000) + "!", "^(a+)+\\1$", false));
        assertEquals(BoosterException.Code.VALIDATION, error.code());
      }));
      for (var future : futures) future.get(5, TimeUnit.SECONDS);
    }
    assertTrue(EcmaRegex.matches("foobar", "(?<=foo)bar", false));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    long workers;
    do {
      workers = Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive() && thread.getName().startsWith("booster-regex-")).count();
      if (workers <= 4) break;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    assertTrue(workers <= 4, "Cancelled matchers must terminate instead of leaving runaway threads");
  }
  @Test void validatesResourceBoundsAndPreservesFlagsAsPartOfTheCacheKey() {
    assertFalse(EcmaRegex.matches("ABC", "abc", false)); assertTrue(EcmaRegex.matches("ABC", "abc", true));
    assertThrows(BoosterException.class, () -> EcmaRegex.matches("x", "a".repeat(513), false));
    assertThrows(BoosterException.class, () -> EcmaRegex.matches("x".repeat(EcmaRegex.MAX_INPUT_LENGTH + 1), "x", false));
    assertThrows(BoosterException.class, () -> EcmaRegex.matches("x", "[", false));
    assertTrue(EcmaRegex.matches("x", "x", false));
  }
}
