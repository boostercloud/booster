package io.boostercloud.booster.azure;

import io.boostercloud.booster.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AzurePartitionCheckpointTest {
  @Test void failedWorkKeepsCursorAndAnotherWorkerTakesOverAfterExpiry() {
    var state = new CosmosStateStore(new TestCosmosDocuments()); var clock = new AzureSubscriptionTest.MutableClock();
    var first = new AzurePartitionCheckpoint(state, "first", clock); var other = new AzurePartitionCheckpoint(state, "other", clock);
    assertEquals(-1, first.acquire("0").orElseThrow()); assertTrue(other.acquire("0").isEmpty());
    first.advance("0", 4);
    // Work after sequence4 fails: no progress update. A restarted worker resumes after4.
    clock.now = clock.now.plusSeconds(31);
    assertEquals(4, other.acquire("0").orElseThrow());
    assertThrows(IllegalStateException.class, () -> first.advance("0", 5));
    other.advance("0", 6); other.advance("0", 5);
    assertEquals(6, other.acquire("0").orElseThrow());
  }
  @Test void expiredOwnerCannotAcknowledgeWithoutReacquiring() {
    var state = new InMemoryStateStore(); var clock = new AzureSubscriptionTest.MutableClock();
    var owner = new AzurePartitionCheckpoint(state, "one", clock); owner.acquire("0"); clock.now = clock.now.plusSeconds(30);
    assertThrows(IllegalStateException.class, () -> owner.advance("0", 1));
    assertEquals(-1, owner.acquire("0").orElseThrow()); owner.advance("0", 1);
    assertEquals(1, owner.acquire("0").orElseThrow());
  }
}
