package com.boostercloud.framework.provider.azure.infrastructure.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Java only: whether the Java runtime of the function apps ({@code site_config.application_stack.java_version},
 * {@code BOOSTER_AZURE_JAVA_VERSION}) can load the class files a package contains. A Java {@code N} runtime loads class
 * files up to version {@code N + 44} and throws {@code UnsupportedClassVersionError} for newer ones, so a package
 * compiled for a newer Java than the stack runs cannot serve a single function. The Booster Java port is compiled for
 * Java 27 ({@code ARCHITECTURE.md}) and Azure Functions offers Java stacks up to 21, so with the default settings this
 * is the case; the check makes that visible offline, before anything is deployed.
 *
 * <p>Class path directories and jars are read in order (the application's classes first) and the first class file
 * too new for the runtime is reported. Multi-release entries ({@code META-INF/versions/…}, used only by runtimes of
 * that version), module descriptors ({@code module-info.class}, never loaded on the class path) and files that are not
 * class files or not readable archives are ignored.
 */
public final class JavaRuntimeCompatibility {

  /** The class file version of Java 1. */
  private static final int CLASS_FILE_VERSION_OFFSET = 44;
  private static final Pattern JAVA_VERSION = Pattern.compile("^(?:1\\.)?(\\d+)(?:[.\\-_+].*)?$");

  private JavaRuntimeCompatibility() {}

  /**
   * @param javaVersion a {@code java_version} ({@code 21}, {@code 17}, {@code 11}, {@code 1.8})
   * @return the newest class file version that runtime loads, or empty when the value is not a Java version
   */
  public static OptionalInt maxClassFileVersion(String javaVersion) {
    Matcher matcher = JAVA_VERSION.matcher(javaVersion == null ? "" : javaVersion.strip());
    if (!matcher.matches()) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(matcher.group(1)) + CLASS_FILE_VERSION_OFFSET);
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  /**
   * @param sources the package sources
   * @param javaVersion the function apps' {@code java_version}
   * @return why the runtime cannot load the package, or empty when it can (or when {@code javaVersion} is not a Java
   *     version, which Terraform and Azure then judge)
   */
  public static Optional<String> incompatibility(DeploySources sources, String javaVersion) {
    OptionalInt max = maxClassFileVersion(javaVersion);
    if (max.isEmpty()) {
      return Optional.empty();
    }
    for (Path entry : sources.classpath()) {
      Optional<ClassFile> tooNew = Files.isDirectory(entry)
          ? firstTooNewInDirectory(entry, max.getAsInt())
          : firstTooNewInJar(entry, max.getAsInt());
      if (tooNew.isPresent()) {
        ClassFile classFile = tooNew.get();
        String version = javaVersion.strip();
        return Optional.of("The function apps run Java " + version + " (BOOSTER_AZURE_JAVA_VERSION), which loads class"
            + " files up to version " + max.getAsInt() + ", but " + classFile.location() + " has class file version "
            + classFile.version() + " (Java " + (classFile.version() - CLASS_FILE_VERSION_OFFSET) + "): every function"
            + " would fail with UnsupportedClassVersionError. The Booster Java port and the applications built with"
            + " it are compiled for Java 27, and Azure Functions offers no Java runtime that new (its Java stacks end"
            + " at 21). Set BOOSTER_AZURE_JAVA_VERSION to a Java version the function apps run that loads these"
            + " classes, or set BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK=true if they run another JVM (for example one"
            + " set with the languageWorkers__java__defaultExecutablePath app setting in config.env).");
      }
    }
    return Optional.empty();
  }

  private record ClassFile(String location, int version) {}

  private static Optional<ClassFile> firstTooNewInDirectory(Path directory, int max) {
    try (Stream<Path> files = Files.walk(directory)) {
      List<Path> classFiles = files.filter(Files::isRegularFile).filter(JavaRuntimeCompatibility::isClassPath)
          .filter(path -> !isVersioned(directory.relativize(path).toString().replace('\\', '/'))).sorted().toList();
      for (Path file : classFiles) {
        try (InputStream input = Files.newInputStream(file)) {
          int version = classFileVersion(input);
          if (version > max) {
            return Optional.of(new ClassFile(file.toString(), version));
          }
        }
      }
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the class files of " + directory + ": " + e.getMessage(), e);
    }
  }

  private static Optional<ClassFile> firstTooNewInJar(Path jar, int max) {
    if (!Files.isRegularFile(jar)) {
      return Optional.empty();
    }
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(".class") || isVersioned(name) || isModuleDescriptor(name)) {
          continue;
        }
        try (InputStream input = zip.getInputStream(entry)) {
          int version = classFileVersion(input);
          if (version > max) {
            return Optional.of(new ClassFile(jar + "!" + name, version));
          }
        }
      }
      return Optional.empty();
    } catch (ZipException notAnArchive) {
      return Optional.empty();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the class files of " + jar + ": " + e.getMessage(), e);
    }
  }

  private static boolean isClassPath(Path path) {
    String name = path.getFileName().toString();
    return name.endsWith(".class") && !isModuleDescriptor(name);
  }

  private static boolean isModuleDescriptor(String name) {
    return name.equals("module-info.class") || name.endsWith("/module-info.class");
  }

  private static boolean isVersioned(String relativeName) {
    return relativeName.startsWith("META-INF/versions/");
  }

  /** The major version of a class file, or -1 when the bytes are not a class file. */
  private static int classFileVersion(InputStream input) throws IOException {
    byte[] header = input.readNBytes(8);
    if (header.length < 8 || (header[0] & 0xFF) != 0xCA || (header[1] & 0xFF) != 0xFE
        || (header[2] & 0xFF) != 0xBA || (header[3] & 0xFF) != 0xBE) {
      return -1;
    }
    return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
  }
}
