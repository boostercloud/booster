package com.boostercloud.framework.core;

import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.internal.UserCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Port of {@code importer.ts}: the equivalent of {@code require}-ing every compiled user file so that its decorators
 * run. Java has no import-time side effects, so the importer scans the package of the root class (and its
 * sub-packages) in class-path directories and jars, then
 *
 * <ol>
 *   <li>runs every {@link BoosterConfigurator} (so that the configuration of the environment exists before the
 *       annotations are applied; TypeScript imports the config files along with the rest), and</li>
 *   <li>applies the annotations of every class ({@link Decorators#registerClass(Class)}).</li>
 * </ol>
 *
 * <p>Order is deterministic: classes are sorted by their class-file path ({@code com/x/commands/A.class}), which is
 * the order of TypeScript's directory walk, with nested classes right after their enclosing class. Anonymous, local,
 * synthetic and hidden classes, {@code package-info} and {@code module-info} are skipped. Classes are loaded without
 * running their static initialisers. Each class is imported once per process ({@code require} cache) until
 * {@link Booster#reset()}.
 */
public final class Importer {

  private static final Set<Class<?>> IMPORTED = ConcurrentHashMap.newKeySet();

  private Importer() {}

  /**
   * {@code importUserProjectFiles(codeRootPath)}: imports every class of the root class's package tree that was not
   * imported yet. Exceptions thrown while configuring or registering (duplicate registrations, invalid annotations)
   * propagate unchanged.
   *
   * @param rootClass a class of the root package
   */
  public static void importUserProjectFiles(Class<?> rootClass) {
    List<Class<?>> classes = new ArrayList<>();
    for (Class<?> type : getImportClasses(rootClass)) {
      if (IMPORTED.add(type)) {
        classes.add(type);
      }
    }
    for (Class<?> type : classes) {
      if (isConfigurator(type)) {
        BoosterConfigurator configurator = (BoosterConfigurator) UserCode.newInstance(type);
        configurator.configure();
      }
    }
    for (Class<?> type : classes) {
      Decorators.registerClass(type);
    }
  }

  /**
   * {@code getImportFiles(codeRootPath)}: the classes of the root class's package and its sub-packages, in import
   * order.
   *
   * @param rootClass a class of the root package
   * @return the classes
   */
  public static List<Class<?>> getImportClasses(Class<?> rootClass) {
    ClassLoader loader = rootClass.getClassLoader() == null ? ClassLoader.getSystemClassLoader() : rootClass.getClassLoader();
    String packagePath = rootClass.getPackageName().replace('.', '/');
    TreeMap<String, String> classNamesByKey = new TreeMap<>();
    for (URL url : packageUrls(rootClass, loader, packagePath)) {
      for (String resource : classResources(url, packagePath)) {
        String withoutExtension = resource.substring(0, resource.length() - ".class".length());
        classNamesByKey.putIfAbsent(withoutExtension.replace('$', '\u0001'), withoutExtension.replace('/', '.'));
      }
    }
    List<Class<?>> classes = new ArrayList<>();
    for (String className : classNamesByKey.values()) {
      Class<?> type = load(className, loader);
      if (!type.isAnonymousClass() && !type.isLocalClass() && !type.isSynthetic() && !type.isHidden()) {
        classes.add(type);
      }
    }
    return classes;
  }

  /** Forgets the imported classes ({@link Booster#reset()}). */
  static void reset() {
    IMPORTED.clear();
  }

  private static boolean isConfigurator(Class<?> type) {
    return BoosterConfigurator.class.isAssignableFrom(type) && !type.isInterface()
        && !Modifier.isAbstract(type.getModifiers());
  }

  private static Class<?> load(String className, ClassLoader loader) {
    try {
      return Class.forName(className, false, loader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Could not load the class " + className + " found while scanning the application", e);
    }
  }

  private static List<URL> packageUrls(Class<?> rootClass, ClassLoader loader, String packagePath) {
    List<URL> urls = new ArrayList<>();
    if (packagePath.isEmpty()) {
      CodeSource codeSource = rootClass.getProtectionDomain().getCodeSource();
      if (codeSource != null && codeSource.getLocation() != null) {
        urls.add(codeSource.getLocation());
      }
      return urls;
    }
    try {
      Enumeration<URL> resources = loader.getResources(packagePath);
      while (resources.hasMoreElements()) {
        urls.add(resources.nextElement());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not scan the package " + packagePath, e);
    }
    return urls;
  }

  private static List<String> classResources(URL url, String packagePath) {
    try {
      return switch (url.getProtocol()) {
        case "file" -> directoryClassResources(Path.of(url.toURI()), packagePath);
        case "jar" -> jarClassResources(url, packagePath);
        default -> List.of();
      };
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid class path location " + url, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not scan " + url, e);
    }
  }

  private static List<String> directoryClassResources(Path location, String packagePath) throws IOException {
    if (Files.isRegularFile(location)) {
      try (JarFile jar = new JarFile(location.toFile())) {
        return jarEntries(jar, packagePath);
      }
    }
    if (!Files.isDirectory(location)) {
      return List.of();
    }
    Path packageDirectory = location;
    Path root = location;
    if (!packagePath.isEmpty()) {
      for (int i = 0; i < packagePath.split("/").length; i++) {
        root = root.getParent();
      }
    }
    Path classesRoot = root;
    List<String> resources = new ArrayList<>();
    try (Stream<Path> files = Files.walk(packageDirectory)) {
      files.filter(file -> Files.isRegularFile(file) && isClassFile(file.getFileName().toString()))
          .forEach(file -> resources.add(classesRoot.relativize(file).toString().replace(java.io.File.separatorChar, '/')));
    }
    return resources;
  }

  private static List<String> jarClassResources(URL url, String packagePath) throws IOException {
    URLConnection connection = url.openConnection();
    if (!(connection instanceof JarURLConnection jarConnection)) {
      return List.of();
    }
    jarConnection.setUseCaches(false);
    try (JarFile jar = jarConnection.getJarFile()) {
      return jarEntries(jar, packagePath);
    }
  }

  private static List<String> jarEntries(JarFile jar, String packagePath) {
    String prefix = packagePath.isEmpty() ? "" : packagePath + "/";
    List<String> resources = new ArrayList<>();
    Enumeration<JarEntry> entries = jar.entries();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      String name = entry.getName();
      if (!entry.isDirectory() && name.startsWith(prefix) && !name.startsWith("META-INF/")
          && isClassFile(name.substring(name.lastIndexOf('/') + 1))) {
        resources.add(name);
      }
    }
    return resources;
  }

  private static boolean isClassFile(String fileName) {
    return fileName.endsWith(".class") && !fileName.equals("module-info.class") && !fileName.equals("package-info.class");
  }
}
