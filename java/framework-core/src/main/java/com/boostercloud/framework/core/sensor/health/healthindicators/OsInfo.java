package com.boostercloud.framework.core.sensor.health.healthindicators;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code sensor/health/health-indicators/os-info.ts} ({@code osInfo()} and its result interfaces, modelled as
 * the records {@link OsInfoResult}, {@link OsInfoCpuResult} and {@link OsInfoMemoryResult}): CPU and memory details of
 * the function indicator, with the JSON shape of Node's {@code os.cpus()}/{@code os.totalmem()}/{@code os.freemem()}:
 *
 * <pre>{@code
 * { cpus: [ { cpu: { model, speed, times: { user, nice, sys, idle, irq } }, timesPercentages: [5 integers] } ],
 *   memory: { totalBytes, freeBytes } }
 * }</pre>
 *
 * <p>{@code timesPercentages[i] = Math.round(100 * times[i] / sum(times))} in the order user, nice, sys, idle, irq
 * ({@code null} when the sum is 0, JavaScript's {@code NaN}).
 *
 * <p>The JDK has no per-CPU information. On Linux the times (milliseconds since boot) are read from
 * {@code /proc/stat} and the model and speed (MHz) from {@code /proc/cpuinfo}, like Node does. Elsewhere there is one
 * entry per logical processor ({@link Runtime#availableProcessors()}) with the model {@code os.arch}, speed {@code 0}
 * and times estimated from the system CPU load ({@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}) over
 * the JVM uptime ({@code user} and {@code idle}; the others are {@code 0}). Memory comes from the platform
 * {@code OperatingSystemMXBean} (total and free physical memory).
 */
public final class OsInfo {

  static final Path PROC_STAT = Path.of("/proc/stat");
  static final Path PROC_CPUINFO = Path.of("/proc/cpuinfo");

  /** Linux clock ticks per second of {@code /proc/stat} ({@code USER_HZ}, 100 on every mainstream kernel). */
  static final long USER_HZ = 100;

  private OsInfo() {}

  /**
   * The CPU times of one processor, in milliseconds (Node's {@code os.CpuInfo.times}).
   *
   * @param user time in user mode
   * @param nice time in user mode with low priority
   * @param sys time in system mode
   * @param idle idle time
   * @param irq time servicing interrupts
   */
  public record CpuTimes(long user, long nice, long sys, long idle, long irq) {
    long total() {
      return user + nice + sys + idle + irq;
    }

    List<Long> values() {
      return List.of(user, nice, sys, idle, irq);
    }
  }

  /**
   * One processor (Node's {@code os.CpuInfo}).
   *
   * @param model the model name
   * @param speed the speed in MHz
   * @param times the cumulative times
   */
  public record CpuInfo(String model, long speed, CpuTimes times) {}

  /**
   * Port of {@code OsInfoCpuResult}.
   *
   * @param cpu the processor
   * @param timesPercentages the share of each time, in the order user, nice, sys, idle, irq
   */
  public record OsInfoCpuResult(CpuInfo cpu, List<Integer> timesPercentages) {}

  /**
   * Port of {@code OsInfoMemoryResult}.
   *
   * @param totalBytes total physical memory
   * @param freeBytes free physical memory
   */
  public record OsInfoMemoryResult(long totalBytes, long freeBytes) {}

  /**
   * Port of {@code OsInfoResult}.
   *
   * @param cpus one entry per logical processor
   * @param memory the memory
   */
  public record OsInfoResult(List<OsInfoCpuResult> cpus, OsInfoMemoryResult memory) {}

  /**
   * {@code osInfo()}.
   *
   * @return the CPUs and the memory
   */
  public static OsInfoResult osInfo() {
    return osInfo(PROC_STAT, PROC_CPUINFO);
  }

  static OsInfoResult osInfo(Path procStat, Path procCpuInfo) {
    List<OsInfoCpuResult> cpus = new ArrayList<>();
    for (CpuInfo cpu : cpus(procStat, procCpuInfo)) {
      cpus.add(new OsInfoCpuResult(cpu, timesPercentages(cpu.times())));
    }
    return new OsInfoResult(cpus, memory());
  }

  static List<Integer> timesPercentages(CpuTimes times) {
    long totalTimes = times.total();
    List<Integer> percentages = new ArrayList<>();
    for (long time : times.values()) {
      percentages.add(totalTimes == 0 ? null : (int) Math.round((100.0 * time) / totalTimes));
    }
    return percentages;
  }

  static List<CpuInfo> cpus(Path procStat, Path procCpuInfo) {
    List<CpuInfo> linuxCpus = linuxCpus(procStat, procCpuInfo);
    if (!linuxCpus.isEmpty()) {
      return linuxCpus;
    }
    return estimatedCpus();
  }

  /** Node's Linux implementation: {@code cpuN} lines of {@code /proc/stat} plus {@code /proc/cpuinfo}. */
  static List<CpuInfo> linuxCpus(Path procStat, Path procCpuInfo) {
    List<String> statLines = readLines(procStat);
    List<String> cpuInfoLines = readLines(procCpuInfo);
    List<String> models = new ArrayList<>();
    List<Long> speeds = new ArrayList<>();
    for (String line : cpuInfoLines) {
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();
      if (key.equals("model name")) {
        models.add(value);
      } else if (key.equals("cpu MHz")) {
        speeds.add(parseSpeed(value));
      }
    }
    List<CpuInfo> cpus = new ArrayList<>();
    for (String line : statLines) {
      String[] fields = line.trim().split("\\s+");
      if (fields.length < 8 || !fields[0].matches("cpu\\d+")) {
        continue;
      }
      try {
        long multiplier = 1000 / USER_HZ;
        CpuTimes times = new CpuTimes(Long.parseLong(fields[1]) * multiplier, Long.parseLong(fields[2]) * multiplier,
            Long.parseLong(fields[3]) * multiplier, Long.parseLong(fields[4]) * multiplier,
            Long.parseLong(fields[6]) * multiplier);
        int index = cpus.size();
        String model = index < models.size() ? models.get(index) : (models.isEmpty() ? "unknown" : models.get(0));
        long speed = index < speeds.size() ? speeds.get(index) : (speeds.isEmpty() ? 0 : speeds.get(0));
        cpus.add(new CpuInfo(model, speed, times));
      } catch (NumberFormatException e) {
        // A malformed line is skipped
      }
    }
    return cpus;
  }

  private static long parseSpeed(String value) {
    try {
      return (long) Math.floor(Double.parseDouble(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static List<CpuInfo> estimatedCpus() {
    long uptime = Math.max(0, ManagementFactory.getRuntimeMXBean().getUptime());
    double load = systemCpuLoad();
    long user = Math.round(Math.max(0, load) * uptime);
    CpuTimes times = new CpuTimes(user, 0, 0, uptime - user, 0);
    String model = System.getProperty("os.arch", "unknown");
    List<CpuInfo> cpus = new ArrayList<>();
    for (int i = 0; i < Math.max(1, Runtime.getRuntime().availableProcessors()); i++) {
      cpus.add(new CpuInfo(model, 0, times));
    }
    return cpus;
  }

  private static double systemCpuLoad() {
    OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
    if (bean instanceof com.sun.management.OperatingSystemMXBean platform) {
      return platform.getCpuLoad();
    }
    return -1;
  }

  private static OsInfoMemoryResult memory() {
    OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
    if (bean instanceof com.sun.management.OperatingSystemMXBean platform) {
      return new OsInfoMemoryResult(platform.getTotalMemorySize(), platform.getFreeMemorySize());
    }
    Runtime runtime = Runtime.getRuntime();
    return new OsInfoMemoryResult(runtime.maxMemory(), runtime.freeMemory());
  }

  private static List<String> readLines(Path path) {
    try {
      return Files.isReadable(path) ? Files.readAllLines(path) : List.of();
    } catch (IOException | RuntimeException e) {
      return List.of();
    }
  }
}
