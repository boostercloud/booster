package io.boostercloud.booster.rockets.webhook;

import java.util.List;
import java.util.Objects;

public record MultipartForm(List<File> files, List<Field> fields) {
  public MultipartForm { files = List.copyOf(files); fields = List.copyOf(fields); }
  public record File(String name, byte[] bufferFile, String filename, String encoding, String mimeType) {
    public File { Objects.requireNonNull(name); bufferFile = bufferFile.clone(); Objects.requireNonNull(filename); }
    @Override public byte[] bufferFile() { return bufferFile.clone(); }
  }
  public record Field(String name, String value, boolean nameTruncated, boolean valueTruncated,
      String encoding, String mimeType) {}
}
