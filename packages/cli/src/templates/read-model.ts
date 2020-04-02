export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@ReadModel({
  authorize: [],
})
export class {{{name}}} {
  public constructor(
    public id: UUID,
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  {{#projections}}
  @Projects({{{entityName}}}, {{{entityId}}})
  public static project{{{entityName}}}(entity: {{{entityName}}}, current{{{name}}}?: {{{name}}}): {{{name}}} {
    return /* NEW {{name}} HERE */
  }

  {{/projections}}
}
`
