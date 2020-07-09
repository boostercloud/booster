export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@EventHandler({{{ event }}})
export class {{{ name }}} {
  public static async handle(event: {{{ event }}}, register: Register): Promise<void> {}
}
`
