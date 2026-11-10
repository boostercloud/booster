package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link NodePath} against Node's {@code path.posix} (expected values computed with Node 24). */
class NodePathTest {

  @ParameterizedTest(name = "path.parse({0}).dir = {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "<empty>|<empty>", "a|<empty>", "client1/myfile.txt|client1", "myfile.txt|<empty>", "a/b/|a", "/a|/", "/|/", "//a|/", "a//b|a/",
      "/client1/sub/f.txt|/client1/sub", "/client1/f.txt?x=a/b|/client1/f.txt?x=a",
      "/client1/my%20file.txt|/client1", "./a|.", "../a/b|../a", ".hidden/x|.hidden"})
  void parse_dir(String path, String dir) {
    assertThat(NodePath.parseDir(unquote(path))).isEqualTo(unquote(dir));
  }

  @ParameterizedTest(name = "path.join({0}) = {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "client1,a.txt|client1/a.txt", "client1/,a.txt|client1/a.txt", "<empty>,<empty>|.", "a,..,..,b|../b", "/x/y,../z|/x/z",
      "a/./b,c/|a/b/c/", "/,a|/a", "clientst,rocketfiles,client1/myfile.txt|clientst/rocketfiles/client1/myfile.txt",
      "a,/b|a/b", "..,a|../a", "a//b,c|a/b/c", "./,<empty>|./"})
  void join(String parts, String joined) {
    String[] arguments = parts.split(",", -1);
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = unquote(arguments[i]);
    }
    assertThat(NodePath.join(arguments)).isEqualTo(joined);
  }

  private static String unquote(String value) {
    return value.equals("<empty>") ? "" : value;
  }
}
