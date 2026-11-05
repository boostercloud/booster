package io.boostercloud.booster.cli;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Safe Java domain scaffolding. Generated handlers fail closed until application logic is supplied. */
public final class DomainGenerator {
    private DomainGenerator() {}

    public enum Kind {
        COMMAND, EVENT, ENTITY, READ_MODEL, QUERY, EVENT_HANDLER, SCHEDULED_COMMAND,
        DATA_MIGRATION, SCHEMA_MIGRATION, TYPE, REDUCER, PROJECTION;

        public static Kind parse(String value) {
            if (value == null) throw new IllegalArgumentException("Domain kind required");
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.equals("MIGRATION")) normalized = "DATA_MIGRATION";
            if (normalized.equals("HANDLER")) normalized = "EVENT_HANDLER";
            if (normalized.equals("SCHEDULE")) normalized = "SCHEDULED_COMMAND";
            try { return Kind.valueOf(normalized); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown domain kind: " + value); }
        }
    }

    public record Field(String name, String type) {
        public Field {
            if (name == null || !SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)
                    || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*")
                    || Set.of("clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait").contains(name))
                throw new IllegalArgumentException("Invalid record field name: " + name);
            type = TypeName.parse(type);
        }
        public static Field parse(String specification) {
            if (specification == null) throw new IllegalArgumentException("Expected field:type");
            int separator = specification.indexOf(':');
            if (separator < 1 || separator != specification.lastIndexOf(':')) throw new IllegalArgumentException("Expected field:type");
            return new Field(specification.substring(0, separator), specification.substring(separator + 1));
        }
    }

    public static Path generate(Path project, String packageName, String kind, String name, List<String> fields) throws IOException {
        return generate(project, packageName, Kind.parse(kind), name, fields.stream().map(Field::parse).toList());
    }

    public static Path generate(Path project, String packageName, Kind kind, String name, List<Field> fields) throws IOException {
        Objects.requireNonNull(kind); Objects.requireNonNull(fields);
        if (packageName == null || !SourceVersion.isName(packageName)
                || !packageName.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) throw new IllegalArgumentException("Invalid Java package");
        if (name == null || !name.matches("[A-Z][A-Za-z0-9_]*") || !SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name))
            throw new IllegalArgumentException("Type name must be a Java identifier starting with an uppercase letter");
        if (fields.size() > 64 || fields.stream().map(Field::name).distinct().count() != fields.size())
            throw new IllegalArgumentException("Record fields must be unique, with at most 64 components");
        boolean recordKind = Set.of(Kind.COMMAND, Kind.EVENT, Kind.ENTITY, Kind.READ_MODEL, Kind.QUERY, Kind.TYPE).contains(kind);
        if (!recordKind && !fields.isEmpty()) throw new IllegalArgumentException("Fields are only supported for record generators");
        var effectiveFields = new ArrayList<>(fields);
        if (Set.of(Kind.EVENT, Kind.ENTITY, Kind.READ_MODEL).contains(kind)) {
            var id = effectiveFields.stream().filter(f -> f.name().equals("id")).findFirst();
            if (id.isEmpty()) effectiveFields.addFirst(new Field("id", "String"));
            else if (!id.get().type().equals("String") && !id.get().type().equals("java.lang.String"))
                throw new IllegalArgumentException("Entity, event and read-model id fields must be String");
        }
        Path root = Objects.requireNonNull(project).toAbsolutePath().normalize();
        checkAncestors(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Generate domain types inside an existing Maven project");
        Path directory = root.resolve("src/main/java").resolve(packageName.replace('.', '/'));
        createDirectories(root, directory);
        Path destination = directory.resolve(name + ".java");
        String code = "package " + packageName + ";\n\n" + source(kind, name, effectiveFields);
        Files.writeString(destination, code, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return destination;
    }

    private static String source(Kind kind, String name, List<Field> fields) {
        String components = fields.stream().map(field -> field.type() + " " + field.name()).collect(Collectors.joining(", "));
        return switch (kind) {
            case TYPE, ENTITY -> "public record " + name + "(" + components + ") {}\n";
            case EVENT -> """
                    import io.boostercloud.booster.core.Booster;
                    import java.util.function.BiFunction;

                    public record %s(%s) {
                      public static <S> Booster.Builder register(Booster.Builder builder, Class<S> entityType,
                                                                BiFunction<%s, S, S> reducer) {
                        return builder.event(%s.class, %s::id, entityType, reducer);
                      }
                    }
                    """.formatted(name, components, name, name, name);
            case COMMAND -> """
                    import io.boostercloud.booster.core.*;

                    public record %s(%s) {
                      public static Booster.Builder register(Booster.Builder builder) {
                        return builder.command(%s.class, AccessPolicy.deny(), %s::handle);
                      }
                      public static Object handle(%s command, Register register) {
                        // Select an authorization policy and emit your registered domain events.
                        throw new UnsupportedOperationException("Implement %s.handle before enabling this command");
                      }
                    }
                    """.formatted(name, components, name, name, name, name);
            case QUERY -> """
                    import io.boostercloud.booster.core.*;
                    import com.fasterxml.jackson.databind.JsonNode;

                    public record %s(%s) {
                      public static Booster.Builder register(Booster.Builder builder) {
                        return builder.query(%s.class, JsonNode.class, AccessPolicy.deny(), %s::handle);
                      }
                      public static JsonNode handle(%s query, QueryContext context) {
                        // Select an authorization policy and return your application's query result.
                        throw new UnsupportedOperationException("Implement %s.handle before enabling this query");
                      }
                    }
                    """.formatted(name, components, name, name, name, name);
            case READ_MODEL -> """
                    import io.boostercloud.booster.core.*;
                    import java.util.function.BiFunction;

                    public record %s(%s) {
                      public static <S> Booster.Builder register(Booster.Builder builder, Class<S> entityType,
                                                                BiFunction<S, %s, %s> projection) {
                        return builder.readModel(%s.class, entityType, AccessPolicy.deny(), projection);
                      }
                    }
                    """.formatted(name, components, name, name, name);
            case EVENT_HANDLER -> """
                    import io.boostercloud.booster.core.*;

                    public final class %s {
                      private %s() {}
                      public static <E> Booster.Builder register(Booster.Builder builder, Class<E> eventType) {
                        return builder.eventHandler("%s", eventType, %s::handle);
                      }
                      public static void handle(Object event, Register register) {
                        // Keep the handler name stable; use idempotency keys for external side effects.
                        throw new UnsupportedOperationException("Implement %s.handle before registering this handler");
                      }
                    }
                    """.formatted(name, name, name, name, name);
            case SCHEDULED_COMMAND -> """
                    import io.boostercloud.booster.core.*;
                    import io.boostercloud.booster.scheduling.*;
                    import java.util.Map;

                    public final class %s {
                      private %s() {}
                      public static ScheduledTask definition(UserContext executionIdentity) {
                        return new ScheduledTask("%s", CalendarSchedule.fromAzureAttributes(Map.of("minute", "0/30")),
                            Json.MAPPER.createObjectNode(), executionIdentity, %s::handle);
                      }
                      public static void handle(ScheduledTask.Occurrence occurrence, Register register) {
                        throw new UnsupportedOperationException("Implement %s.handle before enabling this schedule");
                      }
                    }
                    """.formatted(name, name, name, name, name);
            case DATA_MIGRATION -> """
                    import io.boostercloud.booster.migration.*;

                    public final class %s {
                      private %s() {}
                      public static DataMigration definition(int order) {
                        return new DataMigration("%s", order, %s::start);
                      }
                      public static void start(MigrationContext context) {
                        // Use stable context.step keys, checkpoint after durable work, and call context.finish explicitly.
                        throw new UnsupportedOperationException("Implement %s.start before running this migration");
                      }
                    }
                    """.formatted(name, name, name, name, name);
            case SCHEMA_MIGRATION -> """
                    import io.boostercloud.booster.core.Booster;
                    import com.fasterxml.jackson.databind.JsonNode;

                    public final class %s {
                      private %s() {}
                      public static <T> Booster.Builder register(Booster.Builder builder, Class<T> type, int fromVersion) {
                        return builder.upcaster(type, fromVersion, %s::migrate);
                      }
                      public static JsonNode migrate(JsonNode previous) {
                        // Return a copy with the next schema's fields; configure the type's target version separately.
                        throw new UnsupportedOperationException("Implement %s.migrate before registering this migration");
                      }
                    }
                    """.formatted(name, name, name, name);
            case REDUCER -> """
                    import io.boostercloud.booster.core.Booster;
                    import java.util.function.*;

                    public final class %s<E, S> implements BiFunction<E, S, S> {
                      public static <E, S> Booster.Builder register(Booster.Builder builder, Class<E> eventType,
                          Function<E, String> entityId, Class<S> entityType) {
                        return builder.event(eventType, entityId, entityType, new %s<E, S>());
                      }
                      @Override public S apply(E event, S previous) {
                        // Reducers must be deterministic and return the entity state with its original ID.
                        throw new UnsupportedOperationException("Implement %s.apply before registering this reducer");
                      }
                    }
                    """.formatted(name, name, name);
            case PROJECTION -> """
                    import io.boostercloud.booster.core.*;
                    import java.util.function.BiFunction;

                    public final class %s<S, R> implements BiFunction<S, R, R> {
                      public static <S, R> Booster.Builder register(Booster.Builder builder, Class<S> entityType,
                                                                   Class<R> modelType, AccessPolicy readPolicy) {
                        return builder.readModel(modelType, entityType, readPolicy, new %s<S, R>());
                      }
                      @Override public R apply(S entity, R previous) {
                        // Projections must be deterministic; null deletes the read model.
                        throw new UnsupportedOperationException("Implement %s.apply before registering this projection");
                      }
                    }
                    """.formatted(name, name, name);
        };
    }

    private static void createDirectories(Path root, Path directory) throws IOException {
        Path next = root;
        for (Path component : root.relativize(directory)) {
            next = next.resolve(component);
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(next);
            if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Source path must contain only real directories");
        }
    }
    private static void checkAncestors(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Source path cannot contain symbolic links");
    }

    /** Small type grammar prevents source injection while supporting qualified names, generics and arrays. */
    private static final class TypeName {
        private static final Set<String> PRIMITIVES = Set.of("boolean", "byte", "short", "int", "long", "char", "float", "double");
        private static final Map<String, String> ALIASES = Map.of("Instant", "java.time.Instant", "BigDecimal", "java.math.BigDecimal",
                "UUID", "java.util.UUID", "JsonNode", "com.fasterxml.jackson.databind.JsonNode", "List", "java.util.List",
                "Set", "java.util.Set", "Map", "java.util.Map", "Optional", "java.util.Optional");
        private final String text; private int at;
        private TypeName(String text) { this.text = text; }
        static String parse(String type) {
            if (type == null || type.length() > 256) throw new IllegalArgumentException("Invalid Java field type");
            var parser = new TypeName(type.replaceAll("\\s+", ""));
            String result = parser.type(0, false);
            if (parser.at != parser.text.length()) throw new IllegalArgumentException("Invalid Java field type");
            return result;
        }
        private String type(int depth, boolean genericArgument) {
            if (depth > 4) throw new IllegalArgumentException("Field type is too deeply nested");
            int start = at;
            while (at < text.length() && (Character.isJavaIdentifierPart(text.charAt(at)) || text.charAt(at) == '.')) at++;
            String name = text.substring(start, at);
            boolean primitive = PRIMITIVES.contains(name);
            if (!(primitive || SourceVersion.isName(name)) || Set.of("void", "var", "record", "yield", "sealed", "permits").contains(name))
                throw new IllegalArgumentException("Invalid Java field type");
            String result = ALIASES.getOrDefault(name, name);
            if (at < text.length() && text.charAt(at) == '<') {
                if (primitive) throw new IllegalArgumentException("Primitive types cannot have type arguments");
                at++; var arguments = new ArrayList<String>();
                while (true) {
                    arguments.add(type(depth + 1, true));
                    if (at >= text.length()) throw new IllegalArgumentException("Unclosed generic type");
                    char delimiter = text.charAt(at++);
                    if (delimiter == '>') break;
                    if (delimiter != ',') throw new IllegalArgumentException("Invalid generic type");
                }
                if (Set.of("java.util.List", "java.util.Set", "java.util.Optional").contains(result) && arguments.size() != 1
                        || result.equals("java.util.Map") && arguments.size() != 2)
                    throw new IllegalArgumentException("Wrong number of type arguments");
                result += "<" + String.join(", ", arguments) + ">";
            }
            int arrays = 0;
            while (text.startsWith("[]", at)) { arrays++; at += 2; result += "[]"; }
            if (arrays > 3 || primitive && genericArgument && arrays == 0) throw new IllegalArgumentException("Invalid array or primitive type argument");
            return result;
        }
    }
}
