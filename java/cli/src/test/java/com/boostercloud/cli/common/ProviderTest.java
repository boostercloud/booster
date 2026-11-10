package com.boostercloud.cli.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Port of {@code test/common/provider.test.ts}. */
class ProviderTest {
  @Test
  void azureLabel() {
    assertThat(Provider.AZURE.label()).isEqualTo("com.boostercloud.framework.provider.azure (Azure)");
    assertThat(Provider.AZURE.packageName()).isEqualTo("com.boostercloud.framework.provider.azure");
  }

  @Test
  void localLabel() {
    assertThat(Provider.LOCAL.label()).isEqualTo("com.boostercloud.framework.provider.local (Local only)");
    assertThat(Provider.LOCAL.packageName()).isEqualTo("com.boostercloud.framework.provider.local");
  }

  @Test
  void otherLabel() {
    assertThat(Provider.OTHER.label()).isEqualTo("Other");
    assertThat(Provider.OTHER.packageName()).isEmpty();
  }

  @Test
  void findsProvidersByLabel() {
    assertThat(Provider.fromLabel("Other")).contains(Provider.OTHER);
    assertThat(Provider.fromLabel("AWS")).isEmpty();
  }
}
