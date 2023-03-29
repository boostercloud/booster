/**
 * Allows importing modules dynamically.
 *
 * Implementations should:
 * - Take all the necessary precautions to avoid
 *   code injection attacks, such as validating the module path
 *   and only allowing importing from a whitelist of modules.
 * - Ensure that the files are valid javascript modules,
 *   so no exceptions happen when importing them.
 * - Normalize paths, so they work in all platforms.
 */
export abstract class DynamicImporter {
  /**
   * Import a module dynamically
   */
  abstract import<T>(modulePath: string): Promise<T>
}
