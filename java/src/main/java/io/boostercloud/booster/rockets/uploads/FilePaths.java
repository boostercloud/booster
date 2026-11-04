package io.boostercloud.booster.rockets.uploads;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class FilePaths {
    private FilePaths() {}

    static String directory(String directory) {
        if (directory == null || directory.length() > 1024 || directory.startsWith("/") || directory.contains("\\")
                || directory.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Unsafe directory");
        String value = directory.endsWith("/") ? directory.substring(0, directory.length() - 1) : directory;
        if (!value.isEmpty()) for (String part : value.split("/", -1)) segment(part);
        return value;
    }

    static String segment(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\") || name.length() > 255 || name.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Unsafe file name");
        return name;
    }

    static String key(String directory, String name) {
        String prefix = directory(directory);
        return (prefix.isEmpty() ? "" : prefix + "/") + segment(name);
    }

    static String key(String key) {
        if (key == null || key.isEmpty() || key.endsWith("/")) throw new IllegalArgumentException("Invalid file key");
        int last = key.lastIndexOf('/');
        return key(last < 0 ? "" : key.substring(0, last), key.substring(last + 1));
    }

    static String parent(String key) {
        int last = key.lastIndexOf('/');
        return last < 0 ? "" : key.substring(0, last);
    }

    static String sha256(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
