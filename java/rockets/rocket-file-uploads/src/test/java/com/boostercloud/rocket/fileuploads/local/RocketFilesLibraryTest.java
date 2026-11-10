package com.boostercloud.rocket.fileuploads.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The local runtime library ({@code rocket-file-uploads-local}); TypeScript has no tests for it. */
class RocketFilesLibraryTest {

  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1"));

  @TempDir
  Path project;

  private BoosterConfig config;
  private final RocketFilesLibrary library = new RocketFilesLibrary();

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("local");
    config.setUserProjectRootPath(project + "/");
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
  }

  @Test
  void presigned_urls_point_at_the_local_server() {
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
    assertThat(library.presignedPut(config, CLIENT, "client1", "myfile.txt"))
        .isEqualTo("http://localhost:3000/clientst/rocketfiles/client1/myfile.txt");
    EnvironmentVariables.set(Constants.LOCAL_PORT_ENV, "4000");
    assertThat(library.presignedGet(config, CLIENT, "client1", "myfile.txt"))
        .isEqualTo("http://localhost:4000/clientst/rocketfiles/client1/myfile.txt");
    assertThat(library.presignedGet(config, CLIENT, "client1/", "my file.txt"))
        .isEqualTo("http://localhost:4000/clientst/rocketfiles/client1//my file.txt");
  }

  @Test
  void list_returns_the_entries_sorted_with_their_change_time() throws Exception {
    Path directory = Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    Files.writeString(directory.resolve("b.txt"), "b");
    Files.writeString(directory.resolve("Z.txt"), "Z");
    Files.writeString(directory.resolve("_x"), "_");
    Files.writeString(directory.resolve(".hidden"), "h");
    Files.createDirectories(directory.resolve("sub"));
    Files.setLastModifiedTime(directory.resolve("b.txt"), FileTime.from(Instant.parse("2001-01-01T00:00:00Z")));

    List<ListItem> items = library.list(config, CLIENT, "client1/");

    // byte order of the names (strcmp), not alphabetical
    assertThat(items).extracting(ListItem::name)
        .containsExactly("client1/.hidden", "client1/Z.txt", "client1/_x", "client1/b.txt", "client1/sub");
    ListItem b = items.get(3);
    assertThat(b.metadata()).isNull();
    assertThat(b.properties().createdOn()).isNull();
    assertThat(b.properties().contentLength()).isNull();
    assertThat(b.properties().contentType()).isNull();
    // ctime, not the modification time that was set back to 2001
    assertThat(b.properties().lastModified()).isAfter(Instant.now().minus(1, ChronoUnit.HOURS));
  }

  @Test
  void listing_a_missing_directory_fails_like_node() {
    Path missing = project.resolve("clientst/rocketfiles/nope");
    assertThatThrownBy(() -> library.list(config, CLIENT, "nope"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("ENOENT: no such file or directory, scandir '" + missing + "'");
  }

  @Test
  void delete_removes_files_only() throws Exception {
    Path directory = Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    Files.writeString(directory.resolve("a.txt"), "a");
    Files.createDirectories(directory.resolve("sub"));

    assertThat(library.deleteFile(config, CLIENT, "client1", "a.txt")).isTrue();
    assertThat(directory.resolve("a.txt")).doesNotExist();
    assertThat(library.deleteFile(config, CLIENT, "client1", "a.txt")).isFalse();
    assertThatThrownBy(() -> library.deleteFile(config, CLIENT, "client1", "sub"))
        .hasMessage("EISDIR: illegal operation on a directory, unlink '" + directory.resolve("sub") + "'");
    assertThatThrownBy(() -> library.deleteFile(config, CLIENT, "client1", "../../../x.txt"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid file name ../../../x.txt");
  }

  @Test
  void the_metadata_is_the_request_and_is_always_valid() {
    Map<String, Object> request = Map.of("name", "client1/a.txt");
    assertThat(library.getMetadataFromRequest(request)).isSameAs(request);
    assertThat(library.validateMetadata(CLIENT, request)).isTrue();
    assertThat(library.validateMetadata(CLIENT, null)).isTrue();
  }

  @Test
  void without_project_root_the_working_directory_is_used() {
    assertThat(LocalStorage.projectDirectory(new BoosterConfig("local")))
        .isEqualTo(Path.of(System.getProperty("user.dir")).toAbsolutePath());
    assertThat(LocalStorage.containerDirectory(config, CLIENT)).isEqualTo(project.resolve("clientst/rocketfiles"));
  }
}
