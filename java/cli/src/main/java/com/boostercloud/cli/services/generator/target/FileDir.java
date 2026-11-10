package com.boostercloud.cli.services.generator.target;

/**
 * Where a generated file goes ({@code FileDir}, a {@code Pick} of {@link Target}).
 */
public interface FileDir {
  /** @return the resource name, which is also the file name */
  String name();

  /** @return the file extension, dot included ({@code .java}) */
  String extension();

  /** @return the directory, relative to the project directory */
  String placementDir();

  /**
   * Creates a value.
   *
   * @param name the resource name
   * @param placementDir the directory, relative to the project directory
   * @param extension the extension, dot included
   * @return the file location
   */
  static FileDir of(String name, String placementDir, String extension) {
    record Simple(String name, String extension, String placementDir) implements FileDir {}
    return new Simple(name, extension, placementDir);
  }
}
