export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

// -> Custom code in the event!
@Event
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  public entityID(): UUID {
    return /* the associated entity ID */
  }
}
`
