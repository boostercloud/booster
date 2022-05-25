export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

// -> Custom code in the event handler!
@EventHandler({{{ event }}})
export class {{{ name }}} {
  public static async handle(event: {{{ event }}}, register: Register): Promise<void> {}
}
`
