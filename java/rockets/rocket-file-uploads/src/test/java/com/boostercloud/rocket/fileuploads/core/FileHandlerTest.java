package com.boostercloud.rocket.fileuploads.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketProviderPackageType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code file-handler.ts}, with the local runtime library behind it. */
class FileHandlerTest {

  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1", "shared/**"));
  private static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "cms", List.of("cms1"));

  @TempDir
  Path project;

  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("local");
    config.setUserProjectRootPath(project.toString());
    config.rockets = new ArrayList<>(List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal()));
    EnvironmentVariables.set(Constants.LOCAL_PORT_ENV, "4000");
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
  }

  @Test
  void without_the_rocket_it_fails() {
    BoosterConfig empty = new BoosterConfig("local");
    assertThatThrownBy(() -> new FileHandler(empty))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Rocket not found. Please make sure you have setup the rocket packageName correctly");
    empty.rockets = null;
    assertThatThrownBy(() -> new FileHandler(empty))
        .hasMessage("Rocket not found. Please make sure you have setup the rocket packageName correctly");
    empty.rockets = new ArrayList<>(List.of(new RocketDescriptor("@boostercloud/rocket-file-uploads-local-infrastructure",
        Map.of())));
    assertThatThrownBy(() -> new FileHandler(empty))
        .hasMessage("Rocket not found. Please make sure you have setup the rocket packageName correctly");
  }

  @Test
  void the_first_rocket_descriptor_is_used() {
    config.rockets.add(0, new RocketDescriptor("com.acme.other", Map.of()));
    config.rockets.add(new RocketDescriptor(BoosterRocketFiles.AZURE_INFRASTRUCTURE_PACKAGE,
        new RocketFilesConfiguration(List.of(CMS), RocketProviderPackageType.AZURE)));

    assertThat(new FileHandler(config, "cmsst").<String>presignedPut("cms1", "a.txt"))
        .isEqualTo("http://localhost:4000/cmsst/cms/cms1/a.txt");
  }

  @Test
  void the_storage_is_selected_by_name() {
    assertThat(new FileHandler(config, "CLIENTST").userConfiguration()).isEqualTo(CLIENT);
    assertThatThrownBy(() -> new FileHandler(config)).hasMessage("Missing storageAccountName");
    assertThatThrownBy(() -> new FileHandler(config, "nope")).hasMessage("Storage account name nope not found");
  }

  @Test
  void presigned_urls_of_the_local_library() {
    FileHandler handler = new FileHandler(config, "clientst");

    String put = handler.presignedPut("client1", "myfile.txt");
    assertThat(put).isEqualTo("http://localhost:4000/clientst/rocketfiles/client1/myfile.txt");
    assertThat(handler.presignedGet("shared/a/b", "x y.txt"))
        .isEqualTo("http://localhost:4000/clientst/rocketfiles/shared/a/b/x y.txt");
  }

  @Test
  void every_method_checks_the_directory_first() {
    FileHandler handler = new FileHandler(config, "clientst");

    assertThatThrownBy(() -> handler.presignedGet("client2", "a")).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid directory client2");
    assertThatThrownBy(() -> handler.presignedPut("cms1", "a")).hasMessage("Invalid directory cms1");
    assertThatThrownBy(() -> handler.list("shared")).hasMessage("Invalid directory shared");
    assertThatThrownBy(() -> handler.deleteFile("/client1", "a")).hasMessage("Invalid directory /client1");
  }

  @Test
  void list_and_delete_go_to_the_library() throws Exception {
    Path directory = Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    Files.writeString(directory.resolve("b.txt"), "b");
    Files.writeString(directory.resolve("a.txt"), "a");
    FileHandler handler = new FileHandler(config, "clientst");

    assertThat(handler.list("client1")).extracting(ListItem::name).containsExactly("client1/a.txt", "client1/b.txt");
    assertThat(handler.deleteFile("client1", "a.txt")).isTrue();
    assertThat(handler.deleteFile("client1", "a.txt")).isFalse();
    assertThat(handler.list("client1")).extracting(ListItem::name).containsExactly("client1/b.txt");
  }

  @Test
  void delete_only_removes_a_file_of_the_checked_directory() throws Exception {
    Path secret = Files.createDirectories(project.resolve("clientst/rocketfiles/admin")).resolve("secret.txt");
    Files.writeString(secret, "secret");
    Path nested = Files.createDirectories(project.resolve("clientst/rocketfiles/client1/sub")).resolve("x.txt");
    Files.writeString(nested, "x");
    FileHandler handler = new FileHandler(config, "clientst");

    // 'admin' is not an allowed directory, so a file name must not climb into it from 'client1'
    assertThatThrownBy(() -> handler.deleteFile("admin", "secret.txt")).hasMessage("Invalid directory admin");
    assertThatThrownBy(() -> handler.deleteFile("client1", "../admin/secret.txt"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid file name ../admin/secret.txt");
    // nor go down into a folder the patterns do not allow ('client1/sub' does not match 'client1')
    assertThatThrownBy(() -> handler.deleteFile("client1", "sub/x.txt")).hasMessage("Invalid file name sub/x.txt");
    assertThatThrownBy(() -> handler.deleteFile("client1", "..\\admin\\secret.txt"))
        .hasMessage("Invalid file name ..\\admin\\secret.txt");
    for (String notAFileName : List.of("", ".", "..")) {
      assertThatThrownBy(() -> handler.deleteFile("client1", notAFileName))
          .hasMessage("Invalid file name " + notAFileName);
    }
    assertThat(secret).hasContent("secret");
    assertThat(nested).hasContent("x");
    // file names of the presigned URLs are not validated, as in TypeScript
    assertThat(handler.<String>presignedPut("client1", "sub/x.txt"))
        .isEqualTo("http://localhost:4000/clientst/rocketfiles/client1/sub/x.txt");
  }

  @Test
  void the_provider_library_must_exist() {
    assertThatThrownBy(() -> RocketFilesProviders.load(null)).isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Cannot find module 'undefined'");
    assertThat(RocketFilesProviders.load(RocketProviderPackageType.LOCAL))
        .isSameAs(RocketFilesProviders.load(RocketProviderPackageType.LOCAL))
        .isInstanceOf(com.boostercloud.rocket.fileuploads.local.RocketFilesLibrary.class);
    assertThat(RocketFilesProviders.load(RocketProviderPackageType.AZURE))
        .isInstanceOf(com.boostercloud.rocket.fileuploads.azure.RocketFilesLibrary.class);
  }
}
