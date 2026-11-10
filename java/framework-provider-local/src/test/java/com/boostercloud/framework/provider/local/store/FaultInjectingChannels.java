package com.boostercloud.framework.provider.local.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Persistence.ChannelFactory} whose channels write to the real files but can be told to fail: a write that
 * stops with an {@link IOException} after a given number of bytes (a full disk), or a {@code truncate} that fails. Every
 * write, {@code force} and {@code truncate} is recorded, with the file name, so tests can check that writes are flushed
 * before they are acknowledged.
 */
final class FaultInjectingChannels implements Persistence.ChannelFactory {

  /** Recorded operations, such as {@code "write test.json 42"}, {@code "force test.json false"}. */
  final List<String> log = Collections.synchronizedList(new ArrayList<>());

  private volatile long failWriteAfterBytes = -1;
  private volatile boolean failTruncate;

  /**
   * The next write writes this many bytes, then fails with "No space left on device".
   *
   * @param bytes the bytes written before the failure
   */
  void failNextWriteAfter(long bytes) {
    failWriteAfterBytes = bytes;
  }

  /**
   * @param fail whether {@code truncate} fails
   */
  void failTruncate(boolean fail) {
    failTruncate = fail;
  }

  @Override
  public FileChannel open(Path path, OpenOption... options) throws IOException {
    return new Channel(path.getFileName().toString(), FileChannel.open(path, options));
  }

  private final class Channel extends FileChannel {
    private final String name;
    private final FileChannel delegate;

    Channel(String name, FileChannel delegate) {
      this.name = name;
      this.delegate = delegate;
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
      long limit = failWriteAfterBytes;
      if (limit >= 0) {
        failWriteAfterBytes = -1;
        ByteBuffer prefix = source.slice(source.position(), (int) Math.min(limit, source.remaining()));
        int written = 0;
        while (prefix.hasRemaining()) {
          written += delegate.write(prefix);
        }
        source.position(source.position() + written);
        log.add("partial-write " + name + " " + written);
        throw new IOException("No space left on device");
      }
      int written = delegate.write(source);
      log.add("write " + name + " " + written);
      return written;
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
      if (failTruncate) {
        log.add("failed-truncate " + name + " " + size);
        throw new IOException("Input/output error");
      }
      delegate.truncate(size);
      log.add("truncate " + name + " " + size);
      return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
      delegate.force(metaData);
      log.add("force " + name + " " + metaData);
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
      return delegate.read(destination);
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
      return delegate.read(destinations, offset, length);
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
      throw new UnsupportedOperationException("gathering writes are not used by the store");
    }

    @Override
    public long position() throws IOException {
      return delegate.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
      delegate.position(newPosition);
      return this;
    }

    @Override
    public long size() throws IOException {
      return delegate.size();
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
      return delegate.transferTo(position, count, target);
    }

    @Override
    public long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
      throw new UnsupportedOperationException("transferFrom is not used by the store");
    }

    @Override
    public int read(ByteBuffer destination, long position) throws IOException {
      return delegate.read(destination, position);
    }

    @Override
    public int write(ByteBuffer source, long position) throws IOException {
      throw new UnsupportedOperationException("positional writes are not used by the store");
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
      throw new UnsupportedOperationException("mapping is not used by the store");
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
      return delegate.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
      return delegate.tryLock(position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
      delegate.close();
    }
  }
}
