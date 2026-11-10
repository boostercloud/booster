package com.boostercloud.cli.integration.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.integration.helper.FileHelper.Replacement;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** The helpers' own rules, checked without running the CLI. */
@DisplayName("Helpers")
class HelpersIT {

  @Test
  @DisplayName("expectedOutput matches the steps in order, anything in between, also on long outputs")
  void expectedOutput() {
    var pattern = Expectations.expectedOutput("boost build", "Building project", "Build complete");

    assertThat(pattern.matcher("ℹ boost build 🏗\n- Building project\r\n✔ Building project\nℹ Build complete!").find())
        .isTrue();
    assertThat(pattern.matcher("ℹ Build complete!\nboost build\nBuilding project").find()).isFalse();
    // (.|\n)* would overflow the stack of Java's regex engine on an output like this one
    String longOutput = "boost build\n" + "[INFO] line\n".repeat(200_000) + "Building project\nBuild complete";
    assertThat(pattern.matcher(longOutput).find()).isTrue();
    // the steps are literals
    assertThat(Expectations.expectedOutput("boost debug [local]").matcher("boost debug [local] 🐛").find()).isTrue();
  }

  @Test
  @DisplayName("JavaSources replaces the first occurrence and adds imports after the package declaration")
  void javaSources() {
    String source =
        """
        package com.example.cartdemo.commands;

        import com.boostercloud.framework.core.decorators.Command;

        @Command(authorize = /* X */)
        public record ChangeCart() {}
        """;

    String edited = JavaSources.withImport(JavaSources.replaceFirst(source, "/* X */", "All.class"), JavaSources.ALL);

    assertThat(edited)
        .isEqualTo(
            """
            package com.example.cartdemo.commands;

            import com.boostercloud.framework.core.decorators.All;
            import com.boostercloud.framework.core.decorators.Command;

            @Command(authorize = All.class)
            public record ChangeCart() {}
            """);
    assertThat(JavaSources.withImport("package a;\n\npublic record B() {}\n", "c.D"))
        .isEqualTo("package a;\n\nimport c.D;\n\npublic record B() {}\n");
    assertThat(JavaSources.replaceFirst("x x", "x", "y")).isEqualTo("y x");
    assertThatThrownBy(() -> JavaSources.replaceFirst(source, "/* missing */", "y"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("'/* missing */' is not in:");
  }

  @Test
  @DisplayName("loadFixture applies the replacements in order, every occurrence")
  void loadFixture() {
    String readme =
        FileHelper.loadFixture(
            "cart-demo/README.md",
            Replacement.of("project_name_placeholder", "my-store"),
            Replacement.of("booster_version_placeholder", "9.9.9"),
            Replacement.of("version_placeholder", "unused"));

    assertThat(readme)
        .startsWith("# my-store\n")
        .contains("(Booster CLI 9.9.9)")
        .doesNotContain("project_name_placeholder")
        .doesNotContain("unused")
        .contains("description_placeholder");
  }

  @Test
  @DisplayName("removeFolders deletes trees and ignores missing folders")
  void removeFolders(@TempDir Path directory) throws Exception {
    Path tree = directory.resolve("a/b/c");
    Files.createDirectories(tree);
    Files.writeString(tree.resolve("file.txt"), "x");

    FileHelper.removeFolders(List.of(directory.resolve("a"), directory.resolve("missing")));

    assertThat(directory.resolve("a")).doesNotExist();
  }

  @Test
  @DisplayName("a prompt answer matches the text of its question")
  void promptAnswers() {
    assertThat(PromptAnswer.of("description", "x").matches("? What's your project description? (default: \"\") "))
        .isTrue();
    assertThat(PromptAnswer.of("provider", "").matches("? What's the first version? (default: 0.1.0) ")).isFalse();
    assertThatThrownBy(() -> PromptAnswer.of("provider", null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("the settings use the isolated repository and read the user repository through file://")
  void settingsXml() throws Exception {
    String xml = IsolatedMavenRepository.settingsXml(Path.of("/work/booster-repo"), Path.of("/home/user/.m2/repo"));

    Document settings =
        DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    assertThat(settings.getElementsByTagName("localRepository").item(0).getTextContent())
        .isEqualTo("/work/booster-repo");
    assertThat(settings.getElementsByTagName("url").item(0).getTextContent()).startsWith("file:///home/user/.m2/repo");
    assertThat(settings.getElementsByTagName("pluginRepository").getLength()).isEqualTo(1);
    assertThat(settings.getElementsByTagName("activeProfile").item(0).getTextContent()).isEqualTo("booster-it");
  }

  @Test
  @DisplayName("free ports are distinct and not listening")
  void freePorts() {
    int first = Ports.freePort();
    int second = Ports.freePort();

    assertThat(first).isNotEqualTo(second);
    assertThat(Ports.isListening(first)).isFalse();
  }
}
