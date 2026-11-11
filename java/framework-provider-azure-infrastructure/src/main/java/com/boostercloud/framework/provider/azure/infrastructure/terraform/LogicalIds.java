package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * The logical-id algorithm of CDK for Terraform ({@code makeUniqueId} of {@code lib/private/unique.ts}), applied with
 * the context flags the TypeScript infrastructure writes to {@code cdktf.json}: {@code excludeStackIdFromLogicalIds}
 * (the stack id is not part of the components) and {@code allowSepCharsInLogicalIds} ({@code -} and {@code _} are
 * kept).
 *
 * <ul>
 *   <li>A single component (a direct child of the stack) keeps its id, without the characters outside
 *       {@code [A-Za-z0-9_-]}, when it is at most 255 characters long: {@code mystoreproductionrg}, {@code pip},
 *       {@code ag}.
 *   <li>Otherwise the id is the de-duplicated components (without {@code Resource}) joined and cut to 240
 *       characters, {@code _} and the first 8 upper-case hex digits of the MD5 of the components joined with
 *       {@code /}: the outputs, which TypeScript scopes to the provider construct, are
 *       {@code azureFeaturehttpURL_<hash>}.
 * </ul>
 */
public final class LogicalIds {

  /** Components with this id are skipped ({@code HIDDEN_ID}). */
  public static final String HIDDEN_ID = "Default";

  /** Components with this id are kept in the hash but not in the human part ({@code HIDDEN_FROM_HUMAN_ID}). */
  public static final String HIDDEN_FROM_HUMAN_ID = "Resource";

  private static final int MAX_HUMAN_LEN = 240;
  private static final int MAX_ID_LEN = 255;
  private static final int HASH_LEN = 8;

  private LogicalIds() {}

  /**
   * {@code makeUniqueId(components, allowSepChars)}.
   *
   * @param components the construct ids below the stack
   * @param allowSepChars whether {@code -} and {@code _} are kept
   * @return the logical id
   * @throws IllegalArgumentException {@code Unable to calculate a unique id for an empty set of components}
   */
  public static String makeUniqueId(List<String> components, boolean allowSepChars) {
    List<String> visible = new ArrayList<>();
    for (String component : components) {
      if (!HIDDEN_ID.equals(component)) {
        visible.add(component);
      }
    }
    if (visible.isEmpty()) {
      throw new IllegalArgumentException("Unable to calculate a unique id for an empty set of components");
    }
    if (visible.size() == 1) {
      String topLevelResource = removeNonAllowedSpecialCharacters(visible.get(0), allowSepChars);
      if (topLevelResource.length() <= MAX_ID_LEN) {
        return topLevelResource;
      }
    }
    String hash = pathHash(visible);
    StringBuilder human = new StringBuilder();
    for (String component : removeDupes(visible)) {
      if (!HIDDEN_FROM_HUMAN_ID.equals(component)) {
        human.append(removeNonAllowedSpecialCharacters(component, allowSepChars));
      }
    }
    String humanPart = human.length() > MAX_HUMAN_LEN ? human.substring(0, MAX_HUMAN_LEN) : human.toString();
    return humanPart + "_" + hash;
  }

  /**
   * @param path the components
   * @return the first 8 hex digits (upper case) of the MD5 of the components joined with {@code /}
   */
  static String pathHash(List<String> path) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(String.join("/", path).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, HASH_LEN).toUpperCase(Locale.ROOT);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is not available", e);
    }
  }

  private static List<String> removeDupes(List<String> path) {
    List<String> result = new ArrayList<>();
    for (String component : path) {
      if (result.isEmpty() || !result.get(result.size() - 1).endsWith(component)) {
        result.add(component);
      }
    }
    return result;
  }

  private static String removeNonAllowedSpecialCharacters(String text, boolean allowSepChars) {
    return text.replaceAll(allowSepChars ? "[^A-Za-z0-9_-]" : "[^A-Za-z0-9]", "");
  }
}
