package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link UploadedFileEvent#entityID()} and the reducer of {@link UploadedFileEntity} (md5 values computed with Node). */
class UploadedFileEventTest {

  private static Map<String, Object> localMetadata() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    metadata.put("uri", "http://localhost:3000/clientst/rocketfiles/client1/myfile.txt");
    metadata.put("name", "client1/myfile.txt");
    return metadata;
  }

  @Test
  void the_entity_id_is_the_md5_of_the_metadata_json() {
    assertThat(new UploadedFileEvent(localMetadata()).entityID())
        .isEqualTo(UUID.of("2fbc2683ad694ec4ecb387e886daf4e2"));
  }

  @Test
  void the_md5_uses_javascript_json_text() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("blobTrigger", "rocketfiles/client1/ü \"q\"\n.txt");
    metadata.put("n", 1.5);
    metadata.put("i", 10);
    metadata.put("l", new ArrayList<>(Arrays.asList(true, null, Map.of())));
    assertThat(new UploadedFileEvent(metadata).entityID()).isEqualTo(UUID.of("5dfaeb45700ae31456beb592fb74fd28"));
  }

  @Test
  void the_reducer_replaces_the_state_with_the_md5_id_and_the_metadata() {
    UploadedFileEvent event = new UploadedFileEvent(localMetadata());
    UploadedFileEntity previous = new UploadedFileEntity(UUID.of("old"), Map.of("x", 1));

    UploadedFileEntity entity = UploadedFileEntity.reduceUploadedFileEntity(event, previous);

    assertThat(entity.id()).isEqualTo(UUID.of("2fbc2683ad694ec4ecb387e886daf4e2"));
    assertThat(entity.metadata()).isEqualTo(localMetadata());
    assertThat(UploadedFileEntity.reduceUploadedFileEntity(event, null)).isEqualTo(entity);
  }

  @Test
  void a_stored_event_value_binds_back_with_the_same_id() {
    Object raw = BoosterJson.parse(BoosterJson.toJson(Map.of("metadata", localMetadata())));

    UploadedFileEvent event = Instances.createInstance(UploadedFileEvent.class, raw);

    assertThat(event.entityID()).isEqualTo(UUID.of("2fbc2683ad694ec4ecb387e886daf4e2"));
    assertThat(BoosterJson.toJson(new UploadedFileEntity(event.entityID(), event.metadata())))
        .isEqualTo("{\"id\":\"2fbc2683ad694ec4ecb387e886daf4e2\",\"metadata\":{\"BOOSTER_ROCKET_FUNCTION_ID\":"
            + "\"rocket-files\",\"uri\":\"http://localhost:3000/clientst/rocketfiles/client1/myfile.txt\","
            + "\"name\":\"client1/myfile.txt\"}}");
  }
}
