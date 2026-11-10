package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/notification.test.ts}. */
class NotificationTest extends BoosterTestSupport {

  @Notification
  public record ANotification(String key) {}

  @Notification(topic = "my-topic")
  public record TopicNotification(String key) {}

  @Notification(topic = "")
  public record NoTopicNotification(String key) {}

  @Notification
  public record PartitionedNotification(@PartitionKey String key) {}

  @Notification
  public static class ClassNotification {
    @PartitionKey
    public String key;
  }

  @Notification
  public static class ConstructorParameterNotification {
    public final String key;

    public ConstructorParameterNotification(@PartitionKey String key) {
      this.key = key;
    }
  }

  @Event
  public record EventAndNotification(String key) {}

  @Test
  void addsTheNotificationWithTheDefaultTopic() {
    Decorators.registerClass(ANotification.class);

    assertThat(Booster.config().notifications.get("ANotification")).isEqualTo(new NotificationMetadata(ANotification.class));
    assertThat(Booster.config().eventToTopic.get("ANotification")).isEqualTo("defaultTopic");
    assertThat(Booster.config().topicToEvent.get("defaultTopic")).isEqualTo("ANotification");
  }

  @Test
  void setsTheTopic() {
    Decorators.registerClass(TopicNotification.class);

    assertThat(Booster.config().notifications.get("TopicNotification")).isEqualTo(new NotificationMetadata(TopicNotification.class));
    assertThat(Booster.config().topicToEvent.get("my-topic")).isEqualTo("TopicNotification");
  }

  @Test
  void anEmptyTopicStoresNoMapping() {
    Decorators.registerClass(NoTopicNotification.class);

    assertThat(Booster.config().notifications).containsKey("NoTopicNotification");
    assertThat(Booster.config().eventToTopic).isEmpty();
    assertThat(Booster.config().topicToEvent).isEmpty();
  }

  @Test
  void theLastNotificationOfATopicWins() {
    Decorators.registerClass(ANotification.class);
    Decorators.registerClass(PartitionedNotification.class);

    assertThat(Booster.config().topicToEvent.get("defaultTopic")).isEqualTo("PartitionedNotification");
  }

  @Test
  void setsThePartitionKey() {
    Decorators.registerClass(PartitionedNotification.class);
    Decorators.registerClass(ClassNotification.class);
    Decorators.registerClass(ConstructorParameterNotification.class);

    assertThat(Booster.config().partitionKeys).containsEntry("PartitionedNotification", "key")
        .containsEntry("ClassNotification", "key").containsEntry("ConstructorParameterNotification", "key");
  }

  @Test
  void allowsOnlyOnePartitionKey() {
    Decorators.registerPartitionKey(ANotification.class, "key");
    Decorators.registerPartitionKey(ANotification.class, "key");

    assertThatThrownBy(() -> Decorators.registerPartitionKey(ANotification.class, "other"))
        .hasMessage("Error trying to register a partition key named `other` for class `ANotification`. It already had"
            + " the partition key `key` defined and only one partition key is allowed for each notification event.");
  }

  @Test
  void failsWhenAnEventWithTheSameNameExists() {
    Decorators.registerClass(EventAndNotification.class);

    assertThatThrownBy(() -> Decorators.registerNotification(EventAndNotification.class, null))
        .hasMessage("A notification called EventAndNotification is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }
}
