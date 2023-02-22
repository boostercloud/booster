import { TemplateType } from '../stub-publisher'
import { Target } from './target'

export abstract class FileGenerator {
  /**
   * Generate a file with the given content
   */
  abstract generate<TInfo>(target: Target<TInfo>): Promise<void>

  /**
   * Returns the content of a template
   */
  abstract template(name: TemplateType): Promise<string>
}
