package io.boostercloud.booster.health;

public record HealthConfiguration(String id, String name, boolean enabled, boolean details, boolean showChildren) {
  public HealthConfiguration {
    validatePath(id, false);
    if (name == null || name.isBlank() || name.length() > 256) throw new IllegalArgumentException("Health sensor name is required");
  }
  public HealthConfiguration(String id, String name, boolean enabled, boolean details) {
    this(id, name, enabled, details, true);
  }
  static void validatePath(String path, boolean allowEmpty) {
    if (path == null || (!allowEmpty && path.isEmpty()) || path.length() > 1024)
      throw new IllegalArgumentException("Invalid health component path");
    if (path.isEmpty()) return;
    var segments = path.split("/", -1);
    if (segments.length > 16) throw new IllegalArgumentException("Health component path is too deep");
    for (var segment : segments) if (!segment.matches("[A-Za-z0-9._-]{1,128}") || segment.equals(".") || segment.equals(".."))
      throw new IllegalArgumentException("Invalid health component path");
  }
}
