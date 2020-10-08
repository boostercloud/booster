export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@ScheduledCommand({
  // Specify schedule settings here. By default, it will be triggered every 30 minutes
  minute: '0/30',
})
export class {{{ name }}} {
  public static async handle(register: Register): Promise<void> {
    /* YOUR CODE HERE */
  }
}
`
