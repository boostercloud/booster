package com.boostercloud.cli.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Port of {@code test/common/filenames.test.ts}. */
class FilenamesTest {
  @Nested
  class CheckResourceNameIsValid {
    @Test
    void doesNothingIfResourceNameIsValidPascalCase() {
      assertThatCode(() -> Filenames.checkResourceNameIsValid("TestResource")).doesNotThrowAnyException();
    }

    @Test
    void throwsIfResourceNameIsNotValidPascalCase() {
      String resourceName = "test resource";
      assertThatThrownBy(() -> Filenames.checkResourceNameIsValid(resourceName))
          .isInstanceOf(CliException.class)
          .hasMessage("'" + resourceName + "' is not valid resource name. Please use PascalCase name with valid characters.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Post", "PostReadModel", "Post2", "HTMLParser", "A"})
    void acceptsPascalCaseNames(String name) {
      assertThatCode(() -> Filenames.checkResourceNameIsValid(name)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "2Post", "Post-Created", "Post_Created", "Post Created", "Post$", ""})
    void rejectsOtherNames(String name) {
      assertThatThrownBy(() -> Filenames.checkResourceNameIsValid(name)).isInstanceOf(CliException.class);
    }
  }

  @Nested
  class FormatResourceName {
    @Test
    void fromStringWithSpaces() {
      assertThat(Filenames.formatResourceName("test case")).isEqualTo("TestCase");
    }

    @Test
    void fromSingleCharacterString() {
      assertThat(Filenames.formatResourceName("t")).isEqualTo("T");
    }

    @Test
    void fromStringWithSpacesAndInvalidCharacters() {
      assertThat(Filenames.formatResourceName("_ =& test resource^name*With 21   =Fields"))
          .isEqualTo("TestResourceNameWith21Fields");
    }

    @Test
    void fromStringWithUnderlineCharacters() {
      assertThat(Filenames.formatResourceName("test_resource_name")).isEqualTo("TestResourceName");
    }

    @Test
    void returnsNullWhenStringIsEmpty() {
      assertThat(Filenames.formatResourceName("")).isNull();
    }

    @Test
    void returnsNullWhenStringIsEmptyAfterRemovingInvalidCharacters() {
      assertThat(Filenames.formatResourceName("###!##-==-_-")).isNull();
    }

    @Test
    void keepsSymbolsThatAreNotInTheInvalidCharacterClass() {
      assertThat(Filenames.formatResourceName("@")).isEqualTo("@");
      assertThat(Filenames.hasValidResourceName("@@")).isTrue();
    }
  }

  @Nested
  class TitleCaseString {
    @Test
    void transformsStringToTitleCase() {
      assertThat(Filenames.titleCaseString("test")).isEqualTo("Test");
    }

    @ParameterizedTest
    @CsvSource({"first,First", "second,Second", "third,Third"})
    void transformsStringsToTitleCase(String input, String expected) {
      assertThat(Filenames.titleCaseString(input)).isEqualTo(expected);
    }
  }

  @Nested
  class ClassNameToFileName {
    @Test
    void transformsCamelCaseResourceName() {
      assertThat(Filenames.classNameToFileName("testResource")).isEqualTo("test-resource");
    }

    @Test
    void transformsPascalCaseResourceName() {
      assertThat(Filenames.classNameToFileName("TestResource")).isEqualTo("test-resource");
    }

    @ParameterizedTest
    @CsvSource({
      "ExampleCommand,example-command",
      "PostReadModel,post-read-model",
      "HTMLParser,html-parser",
      "PostV2,post-v2",
      "Post2Created,post2-created",
      "post-created,post-created"
    })
    void followsInflectedRules(String input, String expected) {
      assertThat(Filenames.classNameToFileName(input)).isEqualTo(expected);
    }
  }

  @Nested
  class FileNameWithExtension {
    @Test
    void usesTheJavaExtensionByDefault() {
      assertThat(Filenames.fileNameWithExtension("TestFileName")).isEqualTo("test-file-name.java");
    }

    @Test
    void usesTheGivenExtension() {
      assertThat(Filenames.fileNameWithExtension("TestFileName", "ts")).isEqualTo("test-file-name.ts");
      assertThat(Filenames.fileNameWithExtension("TestFileName", "js")).isEqualTo("test-file-name.js");
    }
  }
}
