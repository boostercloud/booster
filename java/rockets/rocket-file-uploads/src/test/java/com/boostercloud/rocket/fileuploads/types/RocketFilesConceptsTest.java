package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.metadata.ExternalTypes;
import org.junit.jupiter.api.Test;

/** The Java-only registration of the rocket's event, entity and reducer. */
class RocketFilesConceptsTest {

  /** Another class named like the rocket's event. */
  record UploadedFileEvent(Object metadata) {}

  @Test
  void registers_the_event_the_entity_and_the_reducer_once() {
    BoosterConfig config = new BoosterConfig("local");

    RocketFilesConcepts.register(config);
    RocketFilesConcepts.register(config);

    assertThat(config.events).containsEntry("UploadedFileEvent",
        new EventMetadata(com.boostercloud.rocket.fileuploads.types.UploadedFileEvent.class));
    assertThat(config.entities.get("UploadedFileEntity").type()).isEqualTo(UploadedFileEntity.class);
    assertThat(config.reducers).containsEntry("UploadedFileEvent",
        new ReducerMetadata(UploadedFileEntity.class, "reduceUploadedFileEntity"));
    assertThat(ExternalTypes.isExternal(ListItem.class)).isTrue();
  }

  @Test
  void the_entity_events_cannot_be_read_through_graphql() {
    BoosterConfig config = new BoosterConfig("local");
    RocketFilesConcepts.register(config);

    assertThatThrownBy(() -> config.entities.get("UploadedFileEntity").eventStreamAuthorizer().authorize(null, null))
        .isNotNull();
  }

  @Test
  void another_class_with_the_same_name_fails_like_the_decorators() {
    BoosterConfig config = new BoosterConfig("local");
    config.events.put("UploadedFileEvent", new EventMetadata(UploadedFileEvent.class));

    assertThatThrownBy(() -> RocketFilesConcepts.register(config))
        .hasMessageStartingWith("A event called UploadedFileEvent is already registered.");
  }
}
