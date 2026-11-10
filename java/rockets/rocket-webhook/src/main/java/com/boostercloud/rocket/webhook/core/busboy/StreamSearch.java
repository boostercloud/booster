package com.boostercloud.rocket.webhook.core.busboy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Port of {@code streamsearch@1.1.0} ({@code lib/sbmh.js}), the streaming Boyer-Moore-Horspool search Busboy uses to
 * find the multipart boundaries. Chunks are {@linkplain #push pushed} one after the other; the callback receives the
 * data between matches (possibly in several pieces) and is told when the needle was found:
 *
 * <ul>
 *   <li>{@code onData(false, data, start, end, isDataSafe)}: {@code data[start, end)} is haystack that is not part of
 *       a match;</li>
 *   <li>{@code onData(true, data, start, end, isDataSafe)}: a match; {@code data[start, end)} is the haystack right
 *       before it ({@code data} is {@code null} when there is none, JavaScript's {@code undefined}).</li>
 * </ul>
 *
 * <p>{@code isDataSafe} is {@code false} when {@code data} is the internal look-behind buffer, which is overwritten by
 * later pushes. Bytes are compared unsigned.
 */
public final class StreamSearch {

  /** The callback of {@link StreamSearch}. */
  @FunctionalInterface
  public interface Callback {
    /**
     * @param isMatch whether the needle was found right after {@code data[start, end)}
     * @param data the haystack bytes, or {@code null}
     * @param start the first byte
     * @param end the end (exclusive)
     * @param isDataSafe whether {@code data} stays unchanged after the call
     */
    void onData(boolean isMatch, byte[] data, int start, int end, boolean isDataSafe);
  }

  private final byte[] needle;
  private final Callback cb;
  private final int[] occ = new int[256];
  private final byte[] lookbehind;
  private int lookbehindSize;
  private int bufPos;
  private long matches;
  private long maxMatches = Long.MAX_VALUE;

  /**
   * @param needle the text to find, encoded as UTF-8 ({@code Buffer.from(needle)})
   * @param cb the callback
   */
  public StreamSearch(String needle, Callback cb) {
    this(Objects.requireNonNull(needle, "needle").getBytes(StandardCharsets.UTF_8), cb);
  }

  /**
   * @param needle the bytes to find
   * @param cb the callback
   * @throws IllegalArgumentException {@code Missing match callback} without callback
   */
  public StreamSearch(byte[] needle, Callback cb) {
    if (cb == null) {
      throw new IllegalArgumentException("Missing match callback");
    }
    Objects.requireNonNull(needle, "needle");
    this.cb = cb;
    this.needle = needle.clone();
    int needleLen = this.needle.length;
    this.lookbehind = new byte[needleLen];
    Arrays.fill(occ, needleLen);
    // Populate occurrence table with analysis of the needle, ignoring the last letter.
    if (needleLen > 1) {
      for (int i = 0; i < needleLen - 1; ++i) {
        occ[this.needle[i] & 0xFF] = needleLen - 1 - i;
      }
    }
  }

  /**
   * @return how many matches were found since the last {@link #reset()}
   */
  public long matches() {
    return matches;
  }

  /**
   * @param value the number of matches after which the search stops consuming data ({@code Infinity} by default)
   */
  public void maxMatches(long value) {
    this.maxMatches = value;
  }

  /** Forgets the matches and the look-behind data. */
  public void reset() {
    matches = 0;
    lookbehindSize = 0;
    bufPos = 0;
  }

  /**
   * {@code push(chunk, pos)}: searches {@code chunk} from {@code pos}.
   *
   * @param chunk the next haystack chunk
   * @param pos where to start
   * @return the position where the search stopped ({@code chunk.length} unless {@code maxMatches} was reached)
   */
  public int push(byte[] chunk, int pos) {
    int result = -1;
    int chunkLen = chunk.length;
    bufPos = pos;
    while (result != chunkLen && matches < maxMatches) {
      result = feed(chunk);
    }
    return result;
  }

  /** {@code destroy()}: hands the look-behind data (no match) to the callback and resets. */
  public void destroy() {
    int lbSize = lookbehindSize;
    if (lbSize > 0) {
      cb.onData(false, lookbehind, 0, lbSize, false);
    }
    reset();
  }

  private int feed(byte[] data) {
    int len = data.length;
    int needleLen = needle.length;

    // Positive: points to a position in `data`; negative: to a position in the look-behind buffer
    // (-2 points to lookbehind[lookbehindSize - 2]).
    int pos = -lookbehindSize;
    int lastNeedleCharPos = needleLen - 1;
    int lastNeedleChar = needle[lastNeedleCharPos] & 0xFF;
    int end = len - needleLen;

    if (pos < 0) {
      // The look-behind buffer is not empty: Boyer-Moore-Horspool over look-behind + haystack until there is a match,
      // the position no longer needs the look-behind buffer, or the character to look at lies outside the haystack.
      while (pos < 0 && pos <= end) {
        int nextPos = pos + lastNeedleCharPos;
        int ch = nextPos < 0 ? lookbehind[lookbehindSize + nextPos] & 0xFF : data[nextPos] & 0xFF;
        if (ch == lastNeedleChar && matchNeedle(data, pos, lastNeedleCharPos)) {
          // As streamsearch: the size is reset before the test, so the callback never receives look-behind data here
          // (the look-behind buffer always holds a needle prefix, so a match can only start at its first byte).
          lookbehindSize = 0;
          ++matches;
          if (pos > -lookbehindSize) {
            cb.onData(true, lookbehind, 0, lookbehindSize + pos, false);
          } else {
            cb.onData(true, null, 0, 0, true);
          }
          bufPos = pos + needleLen;
          return bufPos;
        }
        pos += occ[ch];
      }

      // No match. There is too little data for Boyer-Moore-Horspool: forward pos until the trailing part of
      // look-behind + data looks like the beginning of the needle, or until pos == 0.
      while (pos < 0 && !matchNeedle(data, pos, len - pos)) {
        ++pos;
      }

      if (pos < 0) {
        // Cut off the part of the look-behind buffer that has been processed and append the whole haystack.
        int bytesToCutOff = lookbehindSize + pos;
        if (bytesToCutOff > 0) {
          // The cut off data is guaranteed not to contain the needle.
          cb.onData(false, lookbehind, 0, bytesToCutOff, false);
        }
        int remaining = lookbehindSize - bytesToCutOff;
        System.arraycopy(lookbehind, bytesToCutOff, lookbehind, 0, remaining);
        lookbehindSize = remaining;
        System.arraycopy(data, 0, lookbehind, lookbehindSize, len);
        lookbehindSize += len;
        bufPos = len;
        return len;
      }

      // Discard the look-behind buffer.
      cb.onData(false, lookbehind, 0, lookbehindSize, false);
      lookbehindSize = 0;
    }

    pos += bufPos;

    int firstNeedleChar = needle[0] & 0xFF;

    // The look-behind buffer is empty: Boyer-Moore-Horspool over this round's haystack only.
    while (pos <= end) {
      int ch = data[pos + lastNeedleCharPos] & 0xFF;
      if (ch == lastNeedleChar && (data[pos] & 0xFF) == firstNeedleChar
          && memcmp(needle, 0, data, pos, lastNeedleCharPos)) {
        ++matches;
        if (pos > 0) {
          cb.onData(true, data, bufPos, pos, true);
        } else {
          cb.onData(true, null, 0, 0, true);
        }
        bufPos = pos + needleLen;
        return bufPos;
      }
      pos += occ[ch];
    }

    // No match. Trailing haystack shorter than the needle is matched from the beginning of the needle instead; what
    // could still be the start of a match goes to the look-behind buffer.
    while (pos < len) {
      if ((data[pos] & 0xFF) != firstNeedleChar || !memcmp(data, pos, needle, 0, len - pos)) {
        ++pos;
        continue;
      }
      System.arraycopy(data, pos, lookbehind, 0, len - pos);
      lookbehindSize = len - pos;
      break;
    }

    // Everything until `pos` is guaranteed not to contain needle data.
    if (pos > 0) {
      cb.onData(false, data, bufPos, Math.min(pos, len), true);
    }

    bufPos = len;
    return len;
  }

  private boolean matchNeedle(byte[] data, int pos, int len) {
    for (int i = 0; i < len; ++i, ++pos) {
      int ch = pos < 0 ? lookbehind[lookbehindSize + pos] & 0xFF : data[pos] & 0xFF;
      if (ch != (needle[i] & 0xFF)) {
        return false;
      }
    }
    return true;
  }

  private static boolean memcmp(byte[] buf1, int pos1, byte[] buf2, int pos2, int num) {
    for (int i = 0; i < num; ++i) {
      if (buf1[pos1 + i] != buf2[pos2 + i]) {
        return false;
      }
    }
    return true;
  }
}
