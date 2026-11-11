package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.testing.TestCli;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/commands/new/command.test.ts} (and of {@code query.test.ts}, which has the same shape). */
class CommandTest extends GeneratorCommandTestBase {
  private static final List<String> DEFAULT_IMPORTS =
      List.of("com.boostercloud.framework.core.decorators.Command", "com.boostercloud.framework.types.concepts.Register");
  private static final List<String> UUID_IMPORTS =
      List.of(
          "com.boostercloud.framework.core.decorators.Command",
          "com.boostercloud.framework.types.concepts.Register",
          "com.boostercloud.framework.types.concepts.UUID");

  private static String renderCommand(List<String> imports, List<String[]> fields) {
    return render(TemplateType.COMMAND, view("com.example.mock.commands", "ExampleCommand", imports, fields));
  }

  @Test
  void initCallsCheckCurrentDirBoosterVersion() {
    assertThatCode(() -> new Command(cli.context()).init()).doesNotThrowAnyException();
  }

  @Nested
  class CreatedCorrectly {
    @Test
    void withNoFields() {
      assertThat(cli.run("new:command", "ExampleCommand").exitCode()).isZero();
      assertThat(read("commands/ExampleCommand")).isEqualTo(renderCommand(DEFAULT_IMPORTS, List.of()));
    }

    @Test
    void withAStringField() {
      cli.run("new:command", "ExampleCommand", "--fields", "title:string");
      assertThat(read("commands/ExampleCommand"))
          .isEqualTo(renderCommand(DEFAULT_IMPORTS, List.<String[]>of(new String[] {"title", "String"})));
    }

    @Test
    void withANumberField() {
      cli.run("new:command", "ExampleCommand", "--fields", "quantity:number");
      assertThat(read("commands/ExampleCommand"))
          .isEqualTo(renderCommand(DEFAULT_IMPORTS, List.<String[]>of(new String[] {"quantity", "double"})));
    }

    @Test
    void withAUuidField() {
      cli.run("new:command", "ExampleCommand", "--fields", "identifier:UUID");
      assertThat(read("commands/ExampleCommand"))
          .isEqualTo(renderCommand(UUID_IMPORTS, List.<String[]>of(new String[] {"identifier", "UUID"})));
    }

    @Test
    void withMultipleFields() {
      cli.run("new:command", "ExampleCommand", "--fields", "title:string", "quantity:number", "identifier:UUID");
      assertThat(read("commands/ExampleCommand"))
          .isEqualTo(
              """
              package com.example.mock.commands;

              import com.boostercloud.framework.core.decorators.Command;
              import com.boostercloud.framework.types.concepts.Register;
              import com.boostercloud.framework.types.concepts.UUID;

              @Command(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
              public record ExampleCommand(
                  String title,
                  double quantity,
                  UUID identifier
              ) {
                public static void handle(ExampleCommand command, Register register) {
                  register.events(/* YOUR EVENT HERE */);
                }
              }
              """);
    }

    @Test
    void importsTheJavaTypesOfTheFields() {
      cli.run("new:command", "ExampleCommand", "--fields", "tags:Array<string>", "at:Date", "ids:UUID[]");
      assertThat(read("commands/ExampleCommand"))
          .contains("import com.boostercloud.framework.types.concepts.UUID;\nimport java.time.Instant;\nimport java.util.List;\n")
          .contains("    List<String> tags,\n    Instant at,\n    List<UUID> ids\n");
    }
  }

  @Nested
  class DisplaysAnError {
    @Test
    void withEmptyCommandName() {
      TestCli.Result result = cli.run("new:command");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided a command name");
      assertThat(nothingGeneratedIn("commands")).isTrue();
    }

    @Test
    void withEmptyFields() {
      assertParseError(cli.run("new:command", "ExampleCommand", "--fields"), "--fields expects a value");
    }

    @Test
    void withFieldWithNoType() {
      assertRuntimeError(cli.run("new:command", "ExampleCommand", "--fields", "title"), "Error parsing field title");
    }

    @Test
    void withNoFieldTypeAfterColon() {
      assertRuntimeError(cli.run("new:command", "ExampleCommand", "--fields", "title:"), "Error parsing field title");
      assertThat(nothingGeneratedIn("commands")).isTrue();
    }

    @Test
    void withRepeatedFields() {
      assertRuntimeError(
          cli.run("new:command", "ExampleCommand", "--fields", "title:string", "title:string", "quantity:number"),
          "Error parsing field title");
      assertThat(nothingGeneratedIn("commands")).isTrue();
    }
  }

  @Nested
  class Queries {
    @Test
    void createsAQuery() {
      assertThat(cli.run("new:query", "ExampleQuery", "--fields", "title:string", "id:UUID").exitCode()).isZero();
      assertThat(read("queries/ExampleQuery"))
          .isEqualTo(
              """
              package com.example.mock.queries;

              import com.boostercloud.framework.core.decorators.Query;
              import com.boostercloud.framework.types.concepts.QueryInfo;
              import com.boostercloud.framework.types.concepts.UUID;

              @Query(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
              public record ExampleQuery(
                  String title,
                  UUID id
              ) {
                public static String handle(ExampleQuery query, QueryInfo queryInfo) {
                  /* YOUR CODE HERE */
                }
              }
              """);
    }

    @Test
    void createsAQueryWithoutFields() {
      cli.run("new:query", "ExampleQuery");
      assertThat(read("queries/ExampleQuery")).contains("public record ExampleQuery() {").doesNotContain("concepts.UUID");
    }

    @Test
    void requiresAName() {
      TestCli.Result result = cli.run("new:query");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided a query name, but it is required, run with --help for usage");
    }

    @Test
    void rejectsMalformedFields() {
      assertRuntimeError(cli.run("new:query", "ExampleQuery", "--fields", "title"), "Error parsing field title");
    }
  }
}
