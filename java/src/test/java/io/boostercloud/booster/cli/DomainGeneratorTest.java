package io.boostercloud.booster.cli;

import io.boostercloud.booster.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DomainGeneratorTest {
    @TempDir Path temporary;
    private Path project() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("application"));
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        return root;
    }

    @Test void everyGeneratedKindCompilesWithThePublicFrameworkApiAndCommandDefaultsToDeny() throws Exception {
        Path root = project();
        List<Path> generated = new ArrayList<>();
        for (var kind : DomainGenerator.Kind.values()) {
            String name = "Generated" + Arrays.stream(kind.name().toLowerCase(Locale.ROOT).split("_"))
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).reduce("", String::concat);
            boolean record = Set.of(DomainGenerator.Kind.COMMAND, DomainGenerator.Kind.EVENT, DomainGenerator.Kind.ENTITY,
                    DomainGenerator.Kind.READ_MODEL, DomainGenerator.Kind.QUERY, DomainGenerator.Kind.TYPE).contains(kind);
            generated.add(DomainGenerator.generate(root, "example.domain", kind.name(), name, record
                    ? List.of("id:String", "quantity:int", "tags:List<Map<String, Integer>>", "createdAt:Instant") : List.of()));
        }
        Path output = Files.createDirectory(temporary.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(generated);
            boolean success = compiler.getTask(null, files, diagnostics,
                    List.of("--release", "27", "-classpath", System.getProperty("java.class.path"), "-d", output.toString()), null, units).call();
            assertTrue(success, () -> diagnostics.getDiagnostics().toString());
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
            var type = loader.loadClass("example.domain.GeneratedCommand");
            var builder = Booster.builder(new InMemoryEventStore());
            type.getMethod("register", Booster.Builder.class).invoke(null, builder);
            try (var booster = builder.build()) {
                var denied = assertThrows(BoosterException.class, () -> booster.executeCommand("GeneratedCommand",
                        Json.MAPPER.createObjectNode().put("id", "one"), RequestContext.anonymous()));
                assertEquals(BoosterException.Code.FORBIDDEN, denied.code());
            }
        }
    }

    @Test void neverOverwritesExistingSourceAndDoesNotChangeOtherFiles() throws Exception {
        Path root = project();
        Path original = DomainGenerator.generate(root, "example.domain", "entity", "Order", List.of("total:long"));
        String contents = Files.readString(original);
        assertTrue(contents.contains("String id, long total"));
        assertThrows(FileAlreadyExistsException.class, () -> DomainGenerator.generate(root, "example.domain", "entity", "Order", List.of()));
        assertEquals(contents, Files.readString(original));
        assertEquals("<project/>\n", Files.readString(root.resolve("pom.xml")));
    }

    @Test void refusesSourceInjectionInvalidTypesAndInvalidIdentityFields() throws Exception {
        Path root = project();
        for (String bad : List.of("x:String;System.exit(0)", "x:List<int>", "x:Map<String>", "x:void", "x:var",
                "x:List<String", "class:String", "toString:String", "x:String[][][][]")) {
            assertThrows(IllegalArgumentException.class,
                    () -> DomainGenerator.generate(root, "example.domain", "command", "Bad", List.of(bad)), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.generate(root, "../outside", "entity", "Order", List.of()));
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.generate(root, "example", "entity", "../Order", List.of()));
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.generate(root, "example", "entity", "Order", List.of("id:int")));
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.generate(root, "example", "command", "Order", List.of("id:String", "id:String")));
        assertFalse(Files.exists(root.resolve("src")));
    }

    @Test void refusesSymlinkSourceAndRequiresAnExistingMavenProject() throws Exception {
        Path root = project();
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.createSymbolicLink(root.resolve("src"), outside);
        assertThrows(java.io.IOException.class, () -> DomainGenerator.generate(root, "example", "command", "Send", List.of()));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.generate(outside, "example", "command", "Send", List.of()));
    }

    @Test void aliasesAreExplicitAndUnknownKindsAreRejected() {
        assertEquals(DomainGenerator.Kind.DATA_MIGRATION, DomainGenerator.Kind.parse("migration"));
        assertEquals(DomainGenerator.Kind.EVENT_HANDLER, DomainGenerator.Kind.parse("event-handler"));
        assertEquals(DomainGenerator.Kind.SCHEDULED_COMMAND, DomainGenerator.Kind.parse("schedule"));
        assertThrows(IllegalArgumentException.class, () -> DomainGenerator.Kind.parse("unknown"));
        assertEquals("java.util.List<java.util.Map<String, Integer>>", new DomainGenerator.Field("values", "List<Map<String, Integer>>").type());
    }
}
