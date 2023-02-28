import { Target } from './target'

export type TemplateType =
  | 'command'
  | 'entity'
  | 'event'
  | 'event-handler'
  | 'read-model'
  | 'scheduled-command'
  | 'type'

export abstract class FileGenerator {
  /**
   * Generate a file with the given content
   */
  abstract generate<TInfo>(target: Target<TInfo>): Promise<void>

  /**
   * Returns the content of a template
   */
  abstract template(name: TemplateType): Promise<string>

  /**
   * Copies stubs to the user project
   */
  abstract copyStubs(): Promise<void>
}
