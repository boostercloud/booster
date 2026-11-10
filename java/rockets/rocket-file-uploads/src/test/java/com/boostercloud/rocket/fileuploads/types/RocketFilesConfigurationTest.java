package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The JSON shapes of the descriptor parameters and of {@code ListItem} (§4.1 and §4.5 of the TypeScript notes). */
class RocketFilesConfigurationTest {

  private static final RocketFilesConfiguration CONFIGURATION = new RocketFilesConfiguration(List.of(
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1", "client2")),
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1", "cms2"))),
      RocketProviderPackageType.LOCAL);

  @Test
  void the_parameters_have_the_typescript_shape() {
    assertThat(BoosterJson.toJson(CONFIGURATION)).isEqualTo("{\"userConfiguration\":["
        + "{\"storageName\":\"clientst\",\"containerName\":\"rocketfiles\",\"directories\":[\"client1\",\"client2\"]},"
        + "{\"storageName\":\"cmsst\",\"containerName\":\"rocketfiles\",\"directories\":[\"cms1\",\"cms2\"]}],"
        + "\"rocketProviderPackage\":\"@boostercloud/rocket-file-uploads-local\"}");
  }

  @Test
  void raw_parameters_are_read_back() {
    Object raw = BoosterJson.toRaw(CONFIGURATION);

    assertThat(RocketFilesConfiguration.from(raw)).isEqualTo(CONFIGURATION);
    assertThat(RocketFilesConfiguration.from(CONFIGURATION)).isSameAs(CONFIGURATION);
    assertThat(RocketFilesConfiguration.from(Map.of("rocketProviderPackage", "@boostercloud/rocket-file-uploads-azure",
        "userConfiguration", List.of())).rocketProviderPackage()).isEqualTo(RocketProviderPackageType.AZURE);
  }

  @Test
  void invalid_parameters_fail() {
    assertThatThrownBy(() -> RocketFilesConfiguration.from(null)).hasMessage("Missing rocket files configuration");
    assertThatThrownBy(() -> RocketFilesConfiguration.from(Map.of("rocketProviderPackage", "@acme/other")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid rocket files configuration");
  }

  @Test
  void the_provider_packages_keep_the_npm_names_and_name_the_java_libraries() {
    assertThat(RocketProviderPackageType.AZURE.packageName()).isEqualTo("@boostercloud/rocket-file-uploads-azure");
    assertThat(RocketProviderPackageType.LOCAL.packageName()).isEqualTo("@boostercloud/rocket-file-uploads-local");
    assertThat(RocketProviderPackageType.LOCAL.libraryClassName())
        .isEqualTo("com.boostercloud.rocket.fileuploads.local.RocketFilesLibrary");
    assertThat(RocketProviderPackageType.fromPackageName("com.boostercloud.rocket.fileuploads.azure"))
        .isEqualTo(RocketProviderPackageType.AZURE);
    assertThatThrownBy(() -> RocketProviderPackageType.fromPackageName("x"))
        .hasMessage("Unknown rocket provider package x");
  }

  @Test
  void list_items_omit_absent_members_and_write_dates_like_javascript() {
    ListItem local = ListItem.of("client1/myfile.txt",
        ListItemProperties.lastModified(Instant.parse("2022-10-26T10:35:18.905Z")));
    ListItem azure = new ListItem("client1/myfile.txt", new ListItemProperties(Instant.parse("2022-10-26T05:40:47Z"),
        Instant.parse("2022-10-26T05:40:47Z"), 6L, "text/plain"), Map.of());

    assertThat(BoosterJson.toJson(local))
        .isEqualTo("{\"name\":\"client1/myfile.txt\",\"properties\":{\"lastModified\":\"2022-10-26T10:35:18.905Z\"}}");
    assertThat(BoosterJson.toJson(azure)).isEqualTo("{\"name\":\"client1/myfile.txt\",\"properties\":{\"createdOn\":"
        + "\"2022-10-26T05:40:47.000Z\",\"lastModified\":\"2022-10-26T05:40:47.000Z\",\"contentLength\":6,"
        + "\"contentType\":\"text/plain\"},\"metadata\":{}}");
  }
}
