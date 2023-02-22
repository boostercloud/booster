/**
 * Interface that represents the interaction
 * with a cloud provider. Implementations must
 * perform all the required checks in order to
 * ensure that the project is properly configured.
 */
export abstract class CloudProvider {
  /**
   * Deploy the project to the cloud provider
   */
  abstract deploy(): Promise<void>

  /**
   * Destroy the project from the cloud provider
   */
  abstract nuke(): Promise<void>

  /**
   * Synthesize the project to a format understandable
   * by the cloud provider
   */
  abstract synth(): Promise<void>

  /**
   * Start the configured provider, in case it
   * supports it
   */
  abstract start(port: number): Promise<void>

  /**
   * Asserts that the name used for the cloud stack is correct
   */
  abstract assertNameIsCorrect(name: string): Promise<void>
}
