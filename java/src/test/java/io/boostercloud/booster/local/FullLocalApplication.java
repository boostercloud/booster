package io.boostercloud.booster.local;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.integration.MigrationProjectionFixture;
import io.boostercloud.booster.migration.DataMigrations;
import io.boostercloud.booster.rockets.uploads.*;
import io.boostercloud.booster.rockets.webhook.*;
import io.boostercloud.booster.transport.*;
import io.boostercloud.booster.transport.auth.JwtVerifier;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate-process application exercising the same local provider as application projects. */
public final class FullLocalApplication implements LocalApplication {
    public record UploadUrl(String directory, String fileName) {}
    public record DownloadUrl(String directory, String fileName) {}
    public record ListFiles(String directory) {}
    public record RunMigrations(boolean resume) {}
    public record MigrationStates() {}
    public record NoteAdded(String id, String text) {}
    public record Note(String id, String text, int deliveries) {}
    public record NoteView(String id, String text, int deliveries) {}
    public record UploadView(String id, String path, long contentLength) {}
    private static final StorageConfiguration STORE = new StorageConfiguration("local", "assets", List.of("incoming", "incoming/**"));
    private final ForwardStorage forward = new ForwardStorage();
    private final FileUploadRocket files = new FileUploadRocket(List.of(STORE), Map.of("local", forward),
            AccessPolicy.roles("operator"), AccessPolicy.roles("reader"));
    private DataMigrations migrations;

    public FullLocalApplication() {}

    @Override public Booster create(LocalProvider provider) {
        var builder = Booster.builder(provider.eventStore()).stateStore(provider.stateStore());
        MigrationProjectionFixture.configure(builder);
        files.install(builder)
                .event(NoteAdded.class, NoteAdded::id, Note.class,
                        (event, old) -> new Note(event.id(), event.text(), old == null ? 1 : old.deliveries() + 1))
                .readModel(NoteView.class, Note.class, AccessPolicy.roles("reader"),
                        (note, old) -> new NoteView(note.id(), note.text(), note.deliveries()))
                .readModel(UploadView.class, UploadedFileEntity.class, AccessPolicy.roles("reader"),
                        (file, old) -> new UploadView(file.id(), file.metadata().path("blobTrigger").asText(), file.metadata().path("contentLength").asLong()))
                .command(UploadUrl.class, String.class, AccessPolicy.roles("operator"), (command, register) ->
                        files.handler(null, register.context()).presignedPut(command.directory(), command.fileName()).toString())
                .query(DownloadUrl.class, String.class, AccessPolicy.roles("reader"), (query, context) ->
                        files.handler(null, context.context()).presignedGet(query.directory(), query.fileName()).toString())
                .query(ListFiles.class, JsonNode.class, AccessPolicy.roles("reader"), (query, context) ->
                        Json.MAPPER.valueToTree(files.handler(null, context.context()).list(query.directory())))
                .command(RunMigrations.class, JsonNode.class, AccessPolicy.roles("operator"), (command, register) -> {
                    if (command.resume()) migrations.resume(); else migrations.run();
                    return Json.MAPPER.valueToTree(migrations.statuses());
                })
                .query(MigrationStates.class, JsonNode.class, AccessPolicy.roles("reader"),
                        (query, context) -> Json.MAPPER.valueToTree(migrations.statuses()));
        var runtime = builder.build();
        migrations = new DataMigrations(runtime, MigrationProjectionFixture.migrations(
                new AtomicBoolean(Boolean.getBoolean("booster.fixture.interruptMigration"))),
                new UserContext("local-migration", Set.of("operator", "reader"), Map.of()));
        return runtime;
    }

    @Override public AuthenticationProvider authentication() {
        try {
            byte[] encoded = Files.readAllBytes(Path.of(System.getProperty("booster.fixture.publicKey")));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            return AuthenticationProvider.jwt(new JwtVerifier(key, "local-fixture", "booster-local"));
        } catch (Exception error) { throw new IllegalStateException("Fixture key unavailable", error); }
    }

    @Override public void configure(Booster runtime, BoosterHttpServer server, LocalProvider provider) throws Exception {
        Path signingKey = provider.directory().resolve("uploads-signing.key");
        if (!Files.exists(signingKey, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            Files.write(signingKey, bytes, StandardOpenOption.CREATE_NEW);
        }
        forward.storage = provider.own(new LocalFileStorage(provider.directory().resolve("uploads"),
                () -> URI.create("http://127.0.0.1:" + server.port()), Files.readAllBytes(signingKey), List.of(STORE),
                notification -> files.fileUploaded(runtime, notification)));
        forward.storage.resumeNotifications();
        server.route("/files", forward.storage);
        var webhooks = WebhookRocket.builder(runtime).authentication(authentication()).strictHttpSemantics(true)
                .route(new WebhookRoute("incoming/note", null, Set.of("POST"), AccessPolicy.roles("operator"), null, null,
                        (request, register) -> {
                            register.emit(new NoteAdded(request.body().path("id").asText(), request.body().path("text").asText()));
                            return WebhookResponse.json(Map.of("accepted", true));
                        })).build();
        server.route("/webhook", webhooks.handler());
    }

    private static final class ForwardStorage implements FileStorage {
        private LocalFileStorage storage;
        public URI presignedPut(StorageConfiguration c, String d, String f, Duration v) { return storage.presignedPut(c, d, f, v); }
        public URI presignedGet(StorageConfiguration c, String d, String f, Duration v) { return storage.presignedGet(c, d, f, v); }
        public List<ListItem> list(StorageConfiguration c, String d) { return storage.list(c, d); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("booster.fixture.publicKey", args[1]);
        System.setProperty("booster.fixture.interruptMigration", args[2]);
        LocalHost.run(new LocalConfiguration(FullLocalApplication.class.getName(), args[0], "127.0.0.1", 0), System.out);
    }
}
