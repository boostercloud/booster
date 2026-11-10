package com.boostercloud.framework.core.sensor.health.healthindicators;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.sensor.health.healthindicators.OsInfo.CpuInfo;
import com.boostercloud.framework.core.sensor.health.healthindicators.OsInfo.CpuTimes;
import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code osInfo()} (the TypeScript tests check it through the health service: non-empty CPUs and positive memory). */
class OsInfoTest {

  @TempDir
  Path directory;

  @Test
  void describesEveryCpuAndThePhysicalMemory() {
    OsInfo.OsInfoResult info = OsInfo.osInfo();

    assertThat(info.cpus()).isNotEmpty();
    assertThat(info.cpus().get(0).timesPercentages()).hasSize(5);
    assertThat(info.memory().totalBytes()).isPositive();
    assertThat(info.memory().freeBytes()).isPositive();
  }

  @Test
  void hasTheJsonShapeOfNode() {
    Map<String, Object> raw = BoosterJson.toRawMap(OsInfo.osInfo());

    assertThat(raw).containsOnlyKeys("cpus", "memory");
    Map<String, Object> firstCpu = map(((List<?>) raw.get("cpus")).get(0));
    assertThat(firstCpu.keySet()).containsExactly("cpu", "timesPercentages");
    Map<String, Object> cpu = map(firstCpu.get("cpu"));
    assertThat(cpu.keySet()).containsExactly("model", "speed", "times");
    assertThat(map(cpu.get("times")).keySet()).containsExactly("user", "nice", "sys", "idle", "irq");
    assertThat(map(raw.get("memory")).keySet()).containsExactly("totalBytes", "freeBytes");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @Test
  void roundsEveryShareLikeMathRound() {
    assertThat(OsInfo.timesPercentages(new CpuTimes(812340, 0, 401220, 9120450, 0))).containsExactly(8, 0, 4, 88, 0);
    assertThat(OsInfo.timesPercentages(new CpuTimes(1, 0, 0, 1, 0))).containsExactly(50, 0, 0, 50, 0);
  }

  @Test
  void aZeroTotalGivesNullShares() {
    assertThat(OsInfo.timesPercentages(new CpuTimes(0, 0, 0, 0, 0))).containsExactly(null, null, null, null, null);
  }

  @Test
  void readsTheLinuxProcFiles() throws IOException {
    Path stat = Files.writeString(directory.resolve("stat"), String.join("\n",
        "cpu  300 20 100 9000 50 10 5 0 0 0",
        "cpu0 100 10 50 4500 25 5 3 0 0 0",
        "cpu1 200 10 50 4500 25 5 2 0 0 0",
        "intr 12345"));
    Path cpuInfo = Files.writeString(directory.resolve("cpuinfo"), String.join("\n",
        "processor\t: 0", "model name\t: Test CPU @ 2.40GHz", "cpu MHz\t\t: 2400.123", "",
        "processor\t: 1", "model name\t: Test CPU @ 2.40GHz", "cpu MHz\t\t: 1200.000"));

    List<CpuInfo> cpus = OsInfo.linuxCpus(stat, cpuInfo);

    assertThat(cpus).containsExactly(
        new CpuInfo("Test CPU @ 2.40GHz", 2400, new CpuTimes(1000, 100, 500, 45000, 50)),
        new CpuInfo("Test CPU @ 2.40GHz", 1200, new CpuTimes(2000, 100, 500, 45000, 50)));
  }

  @Test
  void fallsBackToOneEstimatedEntryPerProcessorWithoutProcFiles() {
    List<CpuInfo> cpus = OsInfo.cpus(directory.resolve("missing-stat"), directory.resolve("missing-cpuinfo"));

    assertThat(cpus).hasSize(Math.max(1, Runtime.getRuntime().availableProcessors()));
    assertThat(cpus).allSatisfy(cpu -> {
      assertThat(cpu.model()).isEqualTo(System.getProperty("os.arch"));
      assertThat(cpu.speed()).isZero();
      assertThat(Arrays.asList(cpu.times().nice(), cpu.times().sys(), cpu.times().irq())).containsOnly(0L);
      assertThat(cpu.times().user()).isNotNegative();
      assertThat(cpu.times().idle()).isNotNegative();
    });
  }
}
