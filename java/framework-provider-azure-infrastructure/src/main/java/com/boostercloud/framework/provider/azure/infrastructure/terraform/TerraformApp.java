package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import com.boostercloud.framework.provider.azure.infrastructure.helper.JsonText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The root of the construct tree ({@code App} of CDK for Terraform). {@link #synth()} writes, like {@code app.synth()}
 * with the default output directory:
 *
 * <pre>
 * &lt;outdir&gt;/manifest.json
 * &lt;outdir&gt;/stacks/&lt;stack id&gt;/cdk.tf.json
 * </pre>
 *
 * where {@code outdir} defaults to {@code <project root>/cdktf.out}. Terraform is then run in the stack directory.
 */
public class TerraformApp extends Construct {

  /** The default output directory name ({@code cdktf.out}). */
  public static final String DEFAULT_OUTDIR = "cdktf.out";

  /** The file name of a synthesized stack. */
  public static final String STACK_FILE_NAME = "cdk.tf.json";

  /** The file name of the manifest. */
  public static final String MANIFEST_FILE_NAME = "manifest.json";

  private final Path projectRoot;
  private final Path outdir;

  /**
   * @param projectRoot the project root (where the Terraform state is kept)
   */
  public TerraformApp(Path projectRoot) {
    this(projectRoot, projectRoot.resolve(DEFAULT_OUTDIR));
  }

  /**
   * @param projectRoot the project root
   * @param outdir the synthesis output directory
   */
  public TerraformApp(Path projectRoot, Path outdir) {
    super(null, "");
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
    this.outdir = Objects.requireNonNull(outdir, "outdir").toAbsolutePath().normalize();
  }

  /**
   * @return the project root
   */
  public Path projectRoot() {
    return projectRoot;
  }

  /**
   * @return the synthesis output directory
   */
  public Path outdir() {
    return outdir;
  }

  /**
   * @return the stacks, in creation order
   */
  public List<TerraformStack> stacks() {
    List<TerraformStack> stacks = new ArrayList<>();
    for (Construct child : children()) {
      if (child instanceof TerraformStack stack) {
        stacks.add(stack);
      }
    }
    return stacks;
  }

  /**
   * @param stack a stack of this application
   * @return {@code <outdir>/stacks/<stack id>}, where Terraform runs for that stack
   */
  public Path stackDirectory(TerraformStack stack) {
    return outdir.resolve("stacks").resolve(stack.id());
  }

  /**
   * {@code app.synth()}: writes the manifest and the {@code cdk.tf.json} of every stack.
   *
   * @throws UncheckedIOException when a file cannot be written
   */
  public void synth() {
    Map<String, Object> stacks = new LinkedHashMap<>();
    for (TerraformStack stack : stacks()) {
      Path stackDirectory = stackDirectory(stack);
      write(stackDirectory.resolve(STACK_FILE_NAME), stack.toJson());
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", stack.id());
      entry.put("constructPath", stack.path());
      entry.put("synthesizedStackPath", "stacks/" + stack.id() + "/" + STACK_FILE_NAME);
      entry.put("workingDirectory", "stacks/" + stack.id());
      entry.put("annotations", List.of());
      entry.put("dependencies", List.of());
      stacks.put(stack.id(), entry);
    }
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("version", TerraformStack.CDKTN_VERSION);
    manifest.put("stacks", stacks);
    write(outdir.resolve(MANIFEST_FILE_NAME), JsonText.stringifySorted(manifest, 2));
  }

  private static void write(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }
}
