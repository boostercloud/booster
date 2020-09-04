export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@Command({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone
})
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  public static async handle(command: {{{ name }}} , register: Register): Promise<void> {
    register.events( /* YOUR EVENT HERE */)
  }
}
`
