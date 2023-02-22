/**
 * Interface for asking the user for input
 * in a way that is independent of the fact whether
 * the input is coming from a terminal or from a CI/CD pipeline.
 */
export abstract class UserInput {
  abstract defaultString(message: string, defaultValue?: string): Promise<string>

  abstract defaultChoice(message: string, choices: Array<string>, defaultValue?: string): Promise<string>

  abstract defaultBoolean(message: string, defaultValue?: boolean): Promise<boolean>
}
