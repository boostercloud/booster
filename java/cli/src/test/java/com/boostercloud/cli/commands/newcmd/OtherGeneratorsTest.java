package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.cli.services.TemplateType;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ports of {@code test/commands/new/{event,event-handler,scheduled-command,type}.test.ts} ({@code read-model.test.ts}
 * is {@link ReadModelTest}).
 */
class OtherGeneratorsTest extends GeneratorCommandTestBase {
  @Nested
  class Events {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Event(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void createsAnEventWithFields() {
      cli.run("new:event", "ExampleEvent", "--fields", "title:string", "quantity:number", "identifier:UUID");
      assertThat(read("events/ExampleEvent"))
          .isEqualTo(
              render(
                  TemplateType.EVENT,
                  view(
                      "com.example.mock.events",
                      "ExampleEvent",
                      List.of("com.boostercloud.framework.core.decorators.Event", "com.boostercloud.framework.types.concepts.UUID"),
                      List.of(
                          new String[] {"title", "String"},
                          new String[] {"quantity", "double"},
                          new String[] {"identifier", "UUID"}))))
          .contains("  public UUID entityID() {\n    return /* the associated entity ID */;\n  }\n");
    }

    @Test
    void createsAnEventWithoutFields() {
      cli.run("new:event", "ExampleEvent");
      assertThat(read("events/ExampleEvent")).contains("@Event\npublic record ExampleEvent() {\n");
    }

    @Test
    void displaysErrors() {
      TestCli.Result missing = cli.run("new:event");
      assertThat(missing.exitCode()).isZero();
      assertThat(missing.stderr()).contains("You haven't provided an event name");
      assertParseError(cli.run("new:event", "ExampleEvent", "--fields"), "Flag --fields expects a value");
      assertRuntimeError(cli.run("new:event", "ExampleEvent", "--fields", "title"), "Error parsing field title");
      assertRuntimeError(
          cli.run("new:event", "ExampleEvent", "--fields", "title:string", "title:string"), "Error parsing field title");
      assertThat(nothingGeneratedIn("events")).isTrue();
    }
  }

  @Nested
  class EventHandlers {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new EventHandler(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void createsAnEventHandler() {
      assertThat(cli.run("new:event-handler", "ExampleEventHandler", "--event", "CommentPosted").exitCode()).isZero();
      Map<String, Object> expectedView =
          view(
              "com.example.mock.eventhandlers",
              "ExampleEventHandler",
              List.of(
                  "com.boostercloud.framework.core.decorators.EventHandler",
                  "com.boostercloud.framework.types.concepts.Register",
                  "com.example.mock.events.CommentPosted"),
              List.of());
      expectedView.put("event", "CommentPosted");
      assertThat(read("eventhandlers/ExampleEventHandler")).isEqualTo(render(TemplateType.EVENT_HANDLER, expectedView));
    }

    @Test
    void withNoEvent() {
      TestCli.Result result = cli.run("new:event-handler", "ExampleEventHandler");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided an event, but it is required, run with --help for usage");
      assertThat(nothingGeneratedIn("eventhandlers")).isTrue();
    }

    @Test
    void withEmptyEventHandlerName() {
      TestCli.Result result = cli.run("new:event-handler");
      assertThat(result.stderr()).contains("You haven't provided an event handler name");
    }

    @Test
    void withEmptyEvent() {
      assertParseError(cli.run("new:event-handler", "ExampleEventHandler", "--event"), "--event expects a value");
    }

    @Test
    void withTwoEvents() {
      assertParseError(
          cli.run("new:event-handler", "ExampleEventHandler", "--event", "CommentPosted", "ArticlePosted"),
          "Unexpected argument: ArticlePosted");
      assertThat(nothingGeneratedIn("eventhandlers")).isTrue();
    }
  }

  @Nested
  class ScheduledCommands {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new ScheduledCommand(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void withScheduledCommandName() {
      cli.run("new:scheduled-command", "ExampleScheduledCommand");
      assertThat(read("scheduledcommands/ExampleScheduledCommand"))
          .isEqualTo(
              """
              package com.example.mock.scheduledcommands;

              import com.boostercloud.framework.core.decorators.ScheduledCommand;
              import com.boostercloud.framework.types.concepts.Register;

              @ScheduledCommand(
                  // Specify schedule settings here. By default, it will be triggered every 30 minutes
                  minute = "0/30")
              public class ExampleScheduledCommand {
                public static void handle(Register register) {
                  /* YOUR CODE HERE */
                }
              }
              """);
    }

    @Test
    void withEmptyScheduledCommandName() {
      TestCli.Result result = cli.run("new:scheduled-command");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided a scheduled command name");
      assertThat(nothingGeneratedIn("scheduledcommands")).isTrue();
    }

    @Test
    void withTwoScheduledCommandNames() {
      assertParseError(cli.run("new:scheduled-command", "ExampleScheduledCommand", "AnotherName"), "Unexpected argument: AnotherName");
      assertThat(nothingGeneratedIn("scheduledcommands")).isTrue();
    }
  }

  @Nested
  class Types {
    @Test
    void initCallsCheckCurrentDirBoosterVersion() {
      assertThatCode(() -> new Type(cli.context()).init()).doesNotThrowAnyException();
    }

    @Test
    void withNoFields() {
      cli.run("new:type", "ExampleType");
      assertThat(read("common/ExampleType")).isEqualTo("package com.example.mock.common;\n\npublic record ExampleType() {}\n");
    }

    @Test
    void withMultipleFieldsIncludingAUuid() {
      cli.run("new:type", "ExampleType", "--fields", "title:string", "quantity:number", "identifier:UUID");
      assertThat(read("common/ExampleType"))
          .isEqualTo(
              """
              package com.example.mock.common;

              import com.boostercloud.framework.types.concepts.UUID;

              public record ExampleType(
                  String title,
                  double quantity,
                  UUID identifier
              ) {}
              """);
    }

    @Test
    void importsProjectTypesFromOtherPackages() {
      TestProjects.write(resource("entities/Money"), "package com.example.mock.entities;\npublic record Money() {}\n");
      cli.run("new:type", "Price", "--fields", "amount:Money", "history:Array<Money>");
      assertThat(read("common/Price"))
          .contains("import com.example.mock.entities.Money;\nimport java.util.List;\n")
          .contains("    Money amount,\n    List<Money> history\n");
    }

    @Test
    void displaysErrors() {
      assertThat(cli.run("new:type").stderr()).contains("You haven't provided a type name");
      TestCli.Result emptyFields = cli.run("new:type", "ExampleType", "--fields");
      assertThat(emptyFields.exitCode()).isEqualTo(2);
      assertThat(emptyFields.stderr()).contains("Error: Flag --fields expects a value\n");
      assertRuntimeError(cli.run("new:type", "ExampleType", "--fields", "title"), "Error parsing field title");
      assertRuntimeError(cli.run("new:type", "ExampleType", "--fields", "title:"), "Error parsing field title");
      assertRuntimeError(
          cli.run("new:type", "ExampleType", "--fields", "title:string", "title:string", "quantity:number"),
          "Error parsing field title");
      assertThat(nothingGeneratedIn("common")).isTrue();
    }
  }

  @Test
  void asksBeforeOverwritingAnExistingResource() {
    cli.run("new:type", "ExampleType");
    TestCli refusing = new TestCli(dir).stdin("n\n");
    TestCli.Result refused = refusing.run("new:type", "ExampleType", "--fields", "a:string");
    assertThat(refused.exitCode()).isEqualTo(1);
    assertThat(refused.stdout()).contains("Resource: \"ExampleType.java\" already exists. Do you want to overwrite it?");
    assertThat(refused.stderr()).contains("The 'common' resource \"ExampleType.java\" already exists. Please use another resource name");
    assertThat(read("common/ExampleType")).doesNotContain("String a");

    TestCli accepting = new TestCli(dir).stdin("y\n");
    assertThat(accepting.run("new:type", "ExampleType", "--fields", "a:string").exitCode()).isZero();
    assertThat(read("common/ExampleType")).contains("String a");
  }

  @Test
  void usesCustomStubs() {
    TestProjects.write(dir.resolve("stubs/type.stub"), "package {{{packageName}}};\n// -> Custom code in the type!\nrecord {{{name}}}() {}\n");
    cli.run("new:type", "ExampleType");
    assertThat(read("common/ExampleType")).isEqualTo("package com.example.mock.common;\n// -> Custom code in the type!\nrecord ExampleType() {}\n");
  }

  @Test
  void failsOutsideABoosterProject() {
    TestCli outside = new TestCli(dir.resolve("src"));
    TestCli.Result result = outside.run("new:type", "ExampleType");
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("There was an error when recognizing the application");
  }
}
