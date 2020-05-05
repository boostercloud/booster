export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@Entity
export class {{{name}}} {
  public constructor(
    public id: UUID,
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  {{#events}}
  @Reduces({{{eventName}}})
  public static reduce{{{eventName}}}(event: {{{eventName}}}, current{{{name}}}?: {{{name}}}): {{{name}}} {
    return /* NEW {{name}} HERE */
  }

  {{/events}}
}
`
