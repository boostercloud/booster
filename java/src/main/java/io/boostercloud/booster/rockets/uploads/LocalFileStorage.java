package io.boostercloud.booster.rockets.uploads;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.boostercloud.booster.core.Json;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Durable local file provider. Immutable file versions and atomic manifests retain
 * failed notifications for replay; a filesystem lock prevents competing writers.
 * The signing key is application configuration and must survive process restarts.
 */
public final class LocalFileStorage implements FileStorage, HttpHandler, AutoCloseable {
    private final Path root;
    private final Path objects;
    private final Path manifests;
    private final Supplier<URI> endpoint;
    private final byte[] signingKey;
    private final Clock clock;
    private final long maxFileBytes;
    private final Map<String, StorageConfiguration> configurations;
    private final Consumer<UploadNotification> notifications;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Map<String, Manifest> versions = new LinkedHashMap<>();
    private final Map<String, Manifest> latest = new HashMap<>();
    private long sequence;
    private boolean closed;

    public record Manifest(String storageName, String containerName, String key, String uploadId,
                           long sequence, String hash, String eTag, Instant createdOn, Instant lastModified,
                           long contentLength, String contentType, Map<String, String> metadata, boolean delivered) {
        public Manifest { metadata = Map.copyOf(metadata); }
        Manifest deliveredManifest() { return new Manifest(storageName, containerName, key, uploadId, sequence,
                hash, eTag, createdOn, lastModified, contentLength, contentType, metadata, true); }
    }

    public LocalFileStorage(Path directory, URI endpoint, byte[] signingKey, List<StorageConfiguration> configurations,
                            Consumer<UploadNotification> notifications) throws IOException {
        this(directory, endpoint, signingKey, configurations, notifications, Clock.systemUTC(), 64L * 1024 * 1024);
    }

    public LocalFileStorage(Path directory, URI endpoint, byte[] signingKey, List<StorageConfiguration> configurations,
                            Consumer<UploadNotification> notifications, Clock clock, long maxFileBytes) throws IOException {
        this(directory, constantEndpoint(endpoint), signingKey, configurations, notifications, clock, maxFileBytes);
    }

    /** Resolve the origin after the HTTP host binds an ephemeral port. */
    public LocalFileStorage(Path directory, Supplier<URI> endpoint, byte[] signingKey, List<StorageConfiguration> configurations,
                            Consumer<UploadNotification> notifications) throws IOException {
        this(directory, endpoint, signingKey, configurations, notifications, Clock.systemUTC(), 64L * 1024 * 1024);
    }

    private LocalFileStorage(Path directory, Supplier<URI> endpoint, byte[] signingKey, List<StorageConfiguration> configurations,
                             Consumer<UploadNotification> notifications, Clock clock, long maxFileBytes) throws IOException {
        this.root = directory.toAbsolutePath().normalize();
        checkAncestors(root);
        Files.createDirectories(root);
        if (signingKey == null || signingKey.length < 32) throw new IllegalArgumentException("At least 256 signing-key bits required");
        this.signingKey = signingKey.clone();
        this.endpoint = Objects.requireNonNull(endpoint);
        this.clock = Objects.requireNonNull(clock);
        if (maxFileBytes < 1) throw new IllegalArgumentException("Positive file size bound required");
        this.maxFileBytes = maxFileBytes;
        this.notifications = Objects.requireNonNull(notifications);
        var stores = new LinkedHashMap<String, StorageConfiguration>();
        for (var c : configurations) if (stores.putIfAbsent(c.storageName(), c) != null)
            throw new IllegalArgumentException("Duplicate storage name");
        if (stores.isEmpty()) throw new IllegalArgumentException("Storage configuration required");
        this.configurations = Map.copyOf(stores);
        objects = root.resolve("objects"); manifests = root.resolve("manifests");
        checkAncestors(objects); checkAncestors(manifests);
        Files.createDirectories(objects); Files.createDirectories(manifests);
        Path lockPath = root.resolve(".lock"); checkAncestors(lockPath);
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("File store is already open");
            try (var files = Files.newDirectoryStream(manifests, "*.json")) {
                for (Path file : files) {
                    checkAncestors(file);
                    if (Files.size(file) > 64 * 1024) throw new IOException("Oversized file manifest");
                    Manifest manifest = Json.MAPPER.readValue(Files.readAllBytes(file), Manifest.class);
                    validateManifest(manifest);
                    remember(manifest);
                }
            }
        } catch (Exception error) {
            lockChannel.close();
            if (error instanceof IOException io) throw io;
            throw new IOException("Cannot open file store", error);
        }
    }

    @Override public synchronized URI presignedPut(StorageConfiguration c, String directory, String fileName, Duration validity) {
        return signed("PUT", c, directory, fileName, validity);
    }
    @Override public synchronized URI presignedGet(StorageConfiguration c, String directory, String fileName, Duration validity) {
        return signed("GET", c, directory, fileName, validity);
    }
    private URI signed(String method, StorageConfiguration c, String directory, String fileName, Duration validity) {
        checkOpen(); requireConfiguration(c); c.requireDirectory(directory); FileHandler.validateValidity(validity);
        String key = FilePaths.key(directory, fileName);
        String nonce = UUID.randomUUID().toString();
        long expires = clock.instant().plus(validity).getEpochSecond();
        String path = "/files/" + c.storageName() + "/" + c.containerName() + "/" + key;
        String query = "expires=" + expires + "&nonce=" + nonce + "&signature=" + sign(method, path, expires, nonce);
        URI origin = validateEndpoint(endpoint.get());
        try { return new URI(origin.getScheme(), null, origin.getHost(), origin.getPort(), path, query, null); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid upload URL", error); }
    }

    @Override public synchronized List<ListItem> list(StorageConfiguration c, String directory) {
        checkOpen(); requireConfiguration(c); c.requireDirectory(directory);
        String prefix = FilePaths.directory(directory);
        if (!prefix.isEmpty()) prefix += "/";
        final String selectedPrefix = prefix;
        return latest.values().stream().filter(m -> m.storageName().equals(c.storageName()) && m.containerName().equals(c.containerName())
                        && m.key().startsWith(selectedPrefix))
                .sorted(Comparator.comparing(Manifest::key))
                .map(m -> new ListItem(m.key(), new ListItem.Properties(m.createdOn(), m.lastModified(), m.contentLength(), m.contentType()), m.metadata()))
                .toList();
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("PUT")) { exchange.getResponseHeaders().set("Allow", "GET, PUT"); respond(exchange, 405, "Method not allowed"); return; }
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/", 5);
            if (parts.length != 5 || !parts[1].equals("files")) { respond(exchange, 404, "File route not found"); return; }
            StorageConfiguration c = configurations.get(parts[2]);
            String key = FilePaths.key(parts[4]);
            if (c == null || !c.containerName().equals(parts[3]) || !c.allows(FilePaths.parent(key))) { respond(exchange, 403, "File access denied"); return; }
            Map<String, String> query = signedQuery(exchange.getRequestURI().getRawQuery());
            long expires = Long.parseLong(query.get("expires"));
            String nonce = query.get("nonce");
            if (!nonce.matches("[0-9a-f-]{36}") || expires <= clock.instant().getEpochSecond()
                    || expires > clock.instant().plus(Duration.ofDays(7)).getEpochSecond()
                    || !MessageDigest.isEqual(sign(method, path, expires, nonce).getBytes(StandardCharsets.US_ASCII),
                    query.get("signature").getBytes(StandardCharsets.US_ASCII))) { respond(exchange, 403, "Invalid or expired file URL"); return; }
            if (method.equals("PUT")) upload(exchange, c, key, nonce);
            else download(exchange, c, key);
        } catch (TooLargeException error) { respondSafely(exchange, 413, "File too large"); }
        catch (UploadConflict error) { respondSafely(exchange, 409, "Upload URL was already used with different content"); }
        catch (IllegalArgumentException error) { respondSafely(exchange, 400, "Invalid file request"); }
        catch (RuntimeException error) { respondSafely(exchange, 503, "File processing failed; retry the same upload URL"); }
        finally { exchange.close(); }
    }

    private synchronized void upload(HttpExchange exchange, StorageConfiguration c, String key, String uploadId) throws IOException {
        checkOpen();
        String lengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (lengthHeader != null && Long.parseLong(lengthHeader) > maxFileBytes) throw new TooLargeException();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) contentType = "application/octet-stream";
        if (contentType.length() > 256 || contentType.chars().anyMatch(ch -> ch < 32 || ch == 127))
            throw new IllegalArgumentException("Invalid content type");
        Map<String, String> metadata = metadata(exchange);
        Path temp = Files.createTempFile(objects, ".upload-", ".tmp");
        long size = 0;
        var digest = FilePaths.digest();
        try {
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = exchange.getRequestBody().read(chunk)) != -1) {
                    size += count; if (size > maxFileBytes) throw new TooLargeException();
                    output.write(chunk, 0, count); digest.update(chunk, 0, count);
                }
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            String hash = HexFormat.of().formatHex(digest.digest());
            Manifest oldVersion = versions.get(uploadId);
            if (oldVersion != null) {
                if (!oldVersion.storageName().equals(c.storageName()) || !oldVersion.containerName().equals(c.containerName())
                        || !oldVersion.key().equals(key) || !oldVersion.hash().equals(hash) || !oldVersion.contentType().equals(contentType)
                        || !oldVersion.metadata().equals(metadata)) throw new UploadConflict();
                deliver(oldVersion);
            } else {
                Path content = objects.resolve(uploadId + ".bin"); checkAncestors(content);
                move(temp, content);
                Instant now = clock.instant();
                Manifest old = latest.get(objectId(c.storageName(), c.containerName(), key));
                Manifest manifest = new Manifest(c.storageName(), c.containerName(), key, uploadId, ++sequence,
                        hash, '"' + FilePaths.sha256(hash + uploadId) + '"', old == null ? now : old.createdOn(), now,
                        size, contentType, metadata, false);
                writeManifest(manifest); remember(manifest);
                deliver(manifest);
            }
            exchange.getResponseHeaders().set("ETag", versions.get(uploadId).eTag());
            byte[] response = Json.MAPPER.writeValueAsBytes(Map.of("status", "success", "result", Map.of("message", "File uploaded",
                    "data", Map.of("name", key.substring(key.lastIndexOf('/') + 1), "mimeType", contentType, "size", size))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response);
        } finally { Files.deleteIfExists(temp); }
    }

    private synchronized void download(HttpExchange exchange, StorageConfiguration c, String key) throws IOException {
        checkOpen();
        Manifest value = latest.get(objectId(c.storageName(), c.containerName(), key));
        if (value == null) { respond(exchange, 404, "File not found"); return; }
        Path content = objects.resolve(value.uploadId() + ".bin"); checkAncestors(content);
        exchange.getResponseHeaders().set("Content-Type", value.contentType());
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("ETag", value.eTag());
        exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(key.substring(key.lastIndexOf('/') + 1), StandardCharsets.UTF_8).replace("+", "%20"));
        value.metadata().forEach((name, data) -> exchange.getResponseHeaders().set("x-ms-meta-" + name, data));
        exchange.sendResponseHeaders(200, value.contentLength() == 0 ? -1 : value.contentLength());
        if (value.contentLength() != 0) try (InputStream input = Files.newInputStream(content, LinkOption.NOFOLLOW_LINKS)) {
            input.transferTo(exchange.getResponseBody());
        }
    }

    /** Retry every pending version in durable upload order. A failure remains pending and is surfaced. */
    public synchronized int resumeNotifications() throws IOException {
        checkOpen(); int count = 0;
        for (Manifest manifest : versions.values().stream().sorted(Comparator.comparingLong(Manifest::sequence)).toList()) {
            if (!manifest.delivered()) { deliver(manifest); count++; }
        }
        return count;
    }

    private void deliver(Manifest manifest) throws IOException {
        if (manifest.delivered()) return;
        var metadata = Json.MAPPER.createObjectNode().put("blobTrigger", manifest.containerName() + "/" + manifest.key())
                .put("storageName", manifest.storageName()).put("eTag", manifest.eTag()).put("contentType", manifest.contentType())
                .put("contentLength", manifest.contentLength());
        metadata.set("metadata", Json.MAPPER.valueToTree(manifest.metadata()));
        notifications.accept(new UploadNotification(manifest.storageName(), manifest.containerName(), manifest.key(), manifest.eTag(), metadata));
        Manifest delivered = manifest.deliveredManifest();
        writeManifest(delivered); remember(delivered);
    }

    private void writeManifest(Manifest manifest) throws IOException {
        Path temp = Files.createTempFile(manifests, ".manifest-", ".tmp");
        try {
            Files.write(temp, Json.MAPPER.writeValueAsBytes(manifest), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Path target = manifests.resolve(manifest.uploadId() + ".json"); checkAncestors(target);
            move(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }

    private void remember(Manifest manifest) {
        sequence = Math.max(sequence, manifest.sequence()); versions.put(manifest.uploadId(), manifest);
        latest.compute(objectId(manifest.storageName(), manifest.containerName(), manifest.key()),
                (ignored, current) -> current == null || manifest.sequence() >= current.sequence() ? manifest : current);
    }

    private void validateManifest(Manifest manifest) throws IOException {
        if (!manifest.uploadId().matches("[0-9a-f-]{36}") || !manifest.hash().matches("[0-9a-f]{64}") || manifest.sequence() < 1)
            throw new IOException("Invalid file manifest");
        FilePaths.key(manifest.key());
        Path content = objects.resolve(manifest.uploadId() + ".bin"); checkAncestors(content);
        if (!Files.isRegularFile(content, LinkOption.NOFOLLOW_LINKS) || Files.size(content) != manifest.contentLength())
            throw new IOException("Missing or truncated file content");
    }
    private static String objectId(String storage, String container, String key) { return storage + "/" + container + "/" + key; }
    private void requireConfiguration(StorageConfiguration c) {
        if (!c.equals(configurations.get(c.storageName()))) throw new IllegalArgumentException("Unknown storage configuration");
    }
    private static Supplier<URI> constantEndpoint(URI endpoint) {
        validateEndpoint(endpoint); return () -> endpoint;
    }
    private static URI validateEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getRawUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || endpoint.getPort() == 0 || endpoint.getPort() > 65535
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !(endpoint.getScheme().equals("https") || endpoint.getScheme().equals("http")
                && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(endpoint.getHost())))
            throw new IllegalArgumentException("Endpoint must be an HTTPS origin or loopback HTTP origin with a bound port");
        return endpoint;
    }
    private static Map<String, String> metadata(HttpExchange exchange) {
        var result = new TreeMap<String, String>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-ms-meta-")) {
                String key = lower.substring(10);
                if (!key.matches("[a-z_][a-z0-9_]{0,63}") || values.size() != 1 || values.getFirst().length() > 1024
                        || values.getFirst().chars().anyMatch(c -> c < 32 || c > 126)) throw new IllegalArgumentException("Invalid file metadata");
                result.put(key, values.getFirst());
            }
        });
        if (result.size() > 16) throw new IllegalArgumentException("Too many metadata entries");
        return Map.copyOf(result);
    }
    private static Map<String, String> signedQuery(String query) {
        if (query == null || query.length() > 512) throw new IllegalArgumentException("Missing signed query");
        var result = new HashMap<String, String>();
        for (String item : query.split("&", -1)) {
            String[] pair = item.split("=", 2);
            if (pair.length != 2 || result.putIfAbsent(pair[0], pair[1]) != null) throw new IllegalArgumentException("Invalid signed query");
        }
        if (!result.keySet().equals(Set.of("expires", "nonce", "signature"))) throw new IllegalArgumentException("Invalid signed query");
        return result;
    }
    private String sign(String method, String path, long expires, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    (method + "\n" + path + "\n" + expires + "\n" + nonce).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static void checkAncestors(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not allowed in the file store");
    }
    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { throw new IOException("File store requires atomic rename", error); }
    }
    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = Json.MAPPER.writeValueAsBytes(Map.of("status", "failure", "code", status, "reason", message));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body);
    }
    private static void respondSafely(HttpExchange exchange, int status, String message) throws IOException {
        try { respond(exchange, status, message); } finally { exchange.close(); }
    }
    private void checkOpen() { if (closed) throw new IllegalStateException("File store is closed"); }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; lock.release(); lockChannel.close(); Arrays.fill(signingKey, (byte) 0); }
    }
    private static final class TooLargeException extends RuntimeException {}
    private static final class UploadConflict extends RuntimeException {}
}
