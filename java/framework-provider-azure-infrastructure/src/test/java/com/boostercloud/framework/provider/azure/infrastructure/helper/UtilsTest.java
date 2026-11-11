package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Port of {@code test/infrastructure/helper/utils.test.ts}, plus the cases of the naming table and the other helpers. */
@ExtendWith(TestEnvironment.class)
class UtilsTest {

  @Nested
  class BuildTheTerraformName {
    @Test
    void with_a_simple_name() {
      assertThat(Utils.toTerraformName("name", "")).isEqualTo("name");
    }

    @Test
    void with_a_simple_name_and_suffix() {
      assertThat(Utils.toTerraformName("name", "suffix")).isEqualTo("namesuffix");
    }

    @Test
    void with_a_long_name_and_suffix_return_last_name_characters_and_the_whole_suffix() {
      assertThat(Utils.toTerraformName("0123456789012345678901234", "suffix")).isEqualTo("B89012345678901234suffix");
    }

    @Test
    void with_a_name_and_a_24_characters_suffix_return_name_and_first_suffix_characters() {
      assertThat(Utils.toTerraformName("name", "012345678901234567890123")).isEqualTo("name0123456789012345678");
    }

    @ParameterizedTest
    @CsvSource({
      "mystoreproduction, dispatched-events, oductiondispatchedevents",
      "mystoreproduction, subscriptions-table, mystoreproductionsubscr",
      "mystoreproduction, connections-table, oductionconnectionstable",
      "mystoreproduction, dedup-table, toreproductiondeduptable",
      "CartReadModel, '', cartreadmodel",
      "1abc, '', Babc",
      "My_Store-App, rg, mystoreapprg"
    })
    void follows_the_typescript_rules(String name, String suffix, String expected) {
      assertThat(Utils.toTerraformName(name, suffix)).isEqualTo(expected);
    }

    @Test
    void defaults_the_suffix_to_empty() {
      assertThat(Utils.toTerraformName("Cart_Read-Model")).isEqualTo("cartreadmodel");
    }

    @Test
    void rejects_suffixes_longer_than_24_characters() {
      assertThatThrownBy(() -> Utils.toTerraformName("name", "0123456789012345678901234"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Error. Suffix could not be greater than 24");
    }

    @Test
    void counts_the_suffix_after_cleaning_it() {
      assertThat(Utils.toTerraformName("n", "0123456789-0123456789-0123")).isEqualTo("n0123456789012345678901");
    }
  }

  @Nested
  class CleanAnAzureName {
    @Test
    void with_a_simple_name() {
      assertThat(Utils.toAzureName("name")).isEqualTo("name");
    }

    @Test
    void with_a_name_longer_than_expected() {
      assertThat(Utils.toAzureName("0123456789012345678901234567890")).isEqualTo("012345678901234567890123");
    }

    @Test
    void with_invalid_characters_in_the_name() {
      assertThat(Utils.toAzureName("0-_-1_-_2")).isEqualTo("012");
    }

    @Test
    void keeps_the_case_and_other_characters() {
      assertThat(Utils.toAzureName("My.Store App", 10)).isEqualTo("My.Store A");
    }
  }

  @Nested
  class BuildTheAppPrefix {
    @Test
    void with_a_simple_name() {
      BoosterConfig config = new BoosterConfig("environmentName");
      config.appName = "appName";
      assertThat(Utils.buildAppPrefix(config)).isEqualTo("appnameenvironmentname");
    }

    @Test
    void with_a_name_longer_than_expected() {
      BoosterConfig config = new BoosterConfig("environmentName0123456789");
      config.appName = "appName0123456789";
      assertThat(Utils.buildAppPrefix(config)).isEqualTo("appname0123456789environ");
    }
  }

  @Nested
  class CreateAResourceGroupName {
    @Test
    void with_a_simple_name() {
      assertThat(Utils.createResourceGroupName("appName", "environmentName")).isEqualTo("appNameenvironmentNarg");
    }

    @Test
    void with_a_name_longer_than_expected() {
      assertThat(Utils.createResourceGroupName("appName0123456789", "environmentName0123456789"))
          .isEqualTo("appName0123456789envrg");
    }

    @Test
    void strips_dashes() {
      assertThat(Utils.createResourceGroupName("my-store", "production")).isEqualTo("mystoreproductionrg");
    }

    @Test
    void derives_the_other_names() {
      assertThat(Utils.createFunctionResourceGroupName("rg")).isEqualTo("rgfpr");
      assertThat(Utils.createStreamFunctionResourceGroupName("rg")).isEqualTo("rgfcs");
      assertThat(Utils.createDomainNameLabel("rg")).isEqualTo("rgapis");
    }
  }

  @Nested
  class Region {
    @Test
    void returns_the_region() {
      assertThat(Utils.getDeployRegion()).isEqualTo("East US");
    }

    @Test
    void fails_when_the_region_is_empty() {
      EnvironmentVariables.set("REGION", "");
      assertThatThrownBy(Utils::getDeployRegion)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("REGION was not properly loaded and is required to run the deploy process. Check that you've"
              + " set it in REGION environment variable.");
    }

    @Test
    void writes_the_url_form() {
      EnvironmentVariables.set("REGION", "West Europe 2");
      assertThat(Utils.urlRegion()).isEqualTo("westeurope2");
    }
  }

  @Nested
  class RenderToFile {
    @TempDir Path root;

    @Test
    void renders_a_template_with_the_configuration_creating_the_parent_directories() throws IOException {
      BoosterConfig config = new BoosterConfig("production");
      config.appName = "my-store";
      Utils.renderToFile(config, root, List.of("a", "b.txt"), "app={{appName}} env={{environmentName}}");
      assertThat(Files.readString(root.resolve("a/b.txt"))).isEqualTo("app=my-store env=production");
    }

    @Test
    void does_not_touch_an_existing_file() throws IOException {
      Files.writeString(root.resolve("c.txt"), "existing");
      Utils.renderToFile(new BoosterConfig("production"), root, List.of("c.txt"), "new");
      assertThat(Files.readString(root.resolve("c.txt"))).isEqualTo("existing");
    }
  }

  @Nested
  class WaitForIt {
    @Test
    void returns_the_first_accepted_result() {
      AtomicInteger attempts = new AtomicInteger();
      RecordingSleeper sleeper = new RecordingSleeper();
      AtomicLong now = new AtomicLong(0);
      Integer result = Utils.waitForIt(attempts::incrementAndGet, value -> value == 3, "never", 10000, 1000,
          () -> now.addAndGet(300), sleeper);
      assertThat(result).isEqualTo(3);
      // elapsed 300 then 600 (the start read the clock once): (10000 - 300) % 1000, (10000 - 600) % 1000
      assertThat(sleeper.sleeps()).containsExactly(700L, 400L);
    }

    @Test
    void fails_with_the_message_after_the_timeout() {
      Deque<Long> clock = new ArrayDeque<>(List.of(0L, 500L, 1200L));
      RecordingSleeper sleeper = new RecordingSleeper();
      assertThatThrownBy(() -> Utils.waitForIt(() -> false, value -> value, "Timed out", 1000, 300, clock::poll,
          sleeper))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Timed out");
      assertThat(sleeper.sleeps()).containsExactly(200L);
    }

    @Test
    void propagates_errors_of_the_try_function_immediately() {
      RecordingSleeper sleeper = new RecordingSleeper();
      assertThatThrownBy(() -> Utils.<Boolean>waitForIt(() -> {
        throw new IllegalArgumentException("boom");
      }, value -> value, "never", 1000, 100, () -> 0, sleeper)).hasMessage("boom");
      assertThat(sleeper.sleeps()).isEmpty();
    }
  }
}
