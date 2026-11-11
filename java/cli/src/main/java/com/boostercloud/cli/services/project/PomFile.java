package com.boostercloud.cli.services.project;

import com.boostercloud.cli.common.CliException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A read-only view of a project's {@code pom.xml}: coordinates, properties, dependencies and the source directory.
 * Property references ({@code ${...}}) are interpolated with the pom's own properties and the {@code project.*}
 * built-ins; inherited properties of remote parents are not resolved.
 */
public final class PomFile {
  private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

  /**
   * A dependency of the pom.
   *
   * @param groupId the group
   * @param artifactId the artifact
   * @param version the interpolated version, or {@code null} when it is not declared (managed elsewhere)
   * @param scope the scope, or {@code null}
   */
  public record Dependency(String groupId, String artifactId, String version, String scope) {}

  private final Path path;
  private final Element project;
  private final Map<String, String> properties;

  private PomFile(Path path, Element project) {
    this.path = path;
    this.project = project;
    this.properties = readProperties(project);
  }

  /**
   * Parses a pom file. DOCTYPE declarations and external entities are rejected.
   *
   * @param path the {@code pom.xml} file
   * @return the parsed pom
   * @throws CliException when the file is missing or is not a valid pom
   */
  public static PomFile read(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(
          new org.xml.sax.ErrorHandler() {
            @Override
            public void warning(org.xml.sax.SAXParseException exception) {
              // warnings do not prevent reading the pom
            }

            @Override
            public void error(org.xml.sax.SAXParseException exception) throws SAXException {
              throw exception;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException {
              throw exception;
            }
          });
      Document document = builder.parse(in);
      Element root = document.getDocumentElement();
      if (root == null || !"project".equals(localName(root))) {
        throw new CliException("The file " + path + " is not a Maven pom: its root element is not <project>");
      }
      return new PomFile(path, root);
    } catch (NoSuchFileException e) {
      throw new CliException("Cannot find the file " + path, e);
    } catch (IOException e) {
      throw new CliException("Cannot read the file " + path + ": " + e.getMessage(), e);
    } catch (SAXException | ParserConfigurationException e) {
      throw new CliException("The file " + path + " is not a valid XML file: " + e.getMessage(), e);
    }
  }

  /** @return the location of the file */
  public Path path() {
    return path;
  }

  /** @return the group of the project (inherited from {@code <parent>} when not declared) */
  public String groupId() {
    return text(project, "groupId").or(() -> child(project, "parent").flatMap(p -> text(p, "groupId"))).orElse("");
  }

  /** @return the artifact of the project */
  public String artifactId() {
    return text(project, "artifactId").orElse("");
  }

  /** @return the version of the project (inherited from {@code <parent>} when not declared) */
  public String version() {
    return text(project, "version").or(() -> child(project, "parent").flatMap(p -> text(p, "version"))).orElse("");
  }

  /**
   * A property of the pom, interpolated.
   *
   * @param name the property name, or a {@code project.*} built-in
   * @return the value, if defined
   */
  public Optional<String> property(String name) {
    return Optional.ofNullable(rawProperty(name)).map(this::interpolate);
  }

  /**
   * Replaces the {@code ${...}} references of a value. Unknown references are left untouched.
   *
   * @param value the text
   * @return the interpolated text
   */
  public String interpolate(String value) {
    String current = value;
    for (int round = 0; round < 10 && current.contains("${"); round++) {
      Matcher matcher = PROPERTY_REFERENCE.matcher(current);
      StringBuilder sb = new StringBuilder();
      boolean changed = false;
      while (matcher.find()) {
        String replacement = rawProperty(matcher.group(1));
        if (replacement != null) {
          changed = true;
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } else {
          matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
        }
      }
      matcher.appendTail(sb);
      current = sb.toString();
      if (!changed) {
        break;
      }
    }
    return current;
  }

  /**
   * The dependencies declared in {@code <dependencies>}. A dependency without a version takes it from
   * {@code <dependencyManagement>} when the pom declares it there.
   *
   * @return the dependencies, in declaration order
   */
  public List<Dependency> dependencies() {
    Map<String, String> managedVersions = new LinkedHashMap<>();
    child(project, "dependencyManagement")
        .flatMap(dm -> child(dm, "dependencies"))
        .ifPresent(
            deps -> {
              for (Element dep : children(deps, "dependency")) {
                text(dep, "version")
                    .ifPresent(v -> managedVersions.put(text(dep, "groupId").orElse("") + ":" + text(dep, "artifactId").orElse(""), v));
              }
            });
    List<Dependency> result = new ArrayList<>();
    child(project, "dependencies")
        .ifPresent(
            deps -> {
              for (Element dep : children(deps, "dependency")) {
                String groupId = interpolate(text(dep, "groupId").orElse(""));
                String artifactId = interpolate(text(dep, "artifactId").orElse(""));
                String version =
                    text(dep, "version").or(() -> Optional.ofNullable(managedVersions.get(groupId + ":" + artifactId))).map(this::interpolate).orElse(null);
                result.add(new Dependency(groupId, artifactId, version, text(dep, "scope").orElse(null)));
              }
            });
    return result;
  }

  /**
   * Finds a dependency.
   *
   * @param groupId the group
   * @param artifactId the artifact
   * @return the dependency, if declared
   */
  public Optional<Dependency> dependency(String groupId, String artifactId) {
    return dependencies().stream()
        .filter(d -> d.groupId().equals(groupId) && d.artifactId().equals(artifactId))
        .findFirst();
  }

  /**
   * The {@code <build><sourceDirectory>} of the pom, interpolated.
   *
   * @return the directory as written in the pom, if declared
   */
  public Optional<String> sourceDirectory() {
    return child(project, "build").flatMap(b -> text(b, "sourceDirectory")).map(this::interpolate);
  }

  /**
   * The {@code <build><directory>} of the pom, interpolated (Maven's default is {@code target}).
   *
   * @return the build directory as written in the pom, if declared
   */
  public Optional<String> buildDirectory() {
    return child(project, "build").flatMap(b -> text(b, "directory")).map(this::interpolate);
  }

  private String rawProperty(String name) {
    return switch (name) {
      case "project.version", "pom.version", "version" -> version();
      case "project.groupId", "pom.groupId" -> groupId();
      case "project.artifactId", "pom.artifactId" -> artifactId();
      case "project.basedir", "basedir" -> path.toAbsolutePath().getParent().toString();
      case "project.build.directory" -> path.toAbsolutePath().getParent().resolve("target").toString();
      default -> properties.get(name);
    };
  }

  private static Map<String, String> readProperties(Element project) {
    Map<String, String> result = new LinkedHashMap<>();
    child(project, "properties")
        .ifPresent(
            props -> {
              NodeList nodes = props.getChildNodes();
              for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i) instanceof Element element) {
                  result.put(localName(element), element.getTextContent().trim());
                }
              }
            });
    return result;
  }

  private static Optional<Element> child(Element parent, String name) {
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element element && name.equals(localName(element))) {
        return Optional.of(element);
      }
    }
    return Optional.empty();
  }

  private static List<Element> children(Element parent, String name) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      if (nodes.item(i) instanceof Element element && name.equals(localName(element))) {
        result.add(element);
      }
    }
    return result;
  }

  private static Optional<String> text(Element parent, String name) {
    return child(parent, name).map(e -> e.getTextContent().trim()).filter(s -> !s.isEmpty());
  }

  private static String localName(Element element) {
    String name = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    int colon = name.indexOf(':');
    return colon < 0 ? name : name.substring(colon + 1);
  }
}
