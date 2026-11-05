package io.boostercloud.booster.local;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.boostercloud.booster.core.Json;
import java.io.IOException;
import java.nio.file.*;

public record LocalConfiguration(String applicationClass, String dataDirectory, String host, int port) {
  public LocalConfiguration {
    if (applicationClass == null || !applicationClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))
      throw new IllegalArgumentException("applicationClass must name a LocalApplication implementation");
    if (dataDirectory == null || dataDirectory.isBlank()) throw new IllegalArgumentException("dataDirectory is required");
    if (host == null) host = "127.0.0.1";
    if (!host.equals("127.0.0.1") && !host.equals("0.0.0.0")) throw new IllegalArgumentException("host must be 127.0.0.1 or 0.0.0.0");
    if (port < 0 || port > 65535) throw new IllegalArgumentException("port must be between 0 and 65535");
  }
  /** Relative paths are resolved against the config file, independent of the caller's directory. */
  public static LocalConfiguration read(Path path) throws IOException {
    path = path.toAbsolutePath().normalize();
    LocalConfiguration value;
    try (var input = Files.newInputStream(path)) {
      value = Json.MAPPER.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(input, LocalConfiguration.class);
    }
    Path data = Path.of(value.dataDirectory());
    if (!data.isAbsolute()) data = path.getParent().resolve(data);
    return new LocalConfiguration(value.applicationClass(), data.normalize().toString(), value.host(), value.port());
  }
  public Path directory() { return Path.of(dataDirectory).toAbsolutePath().normalize(); }
  public LocalApplication instantiate() throws ReflectiveOperationException {
    return Class.forName(applicationClass).asSubclass(LocalApplication.class).getConstructor().newInstance();
  }
}
