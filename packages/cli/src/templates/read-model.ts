export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@ReadModel({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone
})
export class {{{name}}} {
  public constructor(
    public id: UUID,
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  {{#projections}}
  @Projects({{{entityName}}}, "{{{entityId}}}")
  public static project{{{entityName}}}(entity: {{{entityName}}}, current{{{name}}}?: {{{name}}}): ProjectionResult<{{{name}}}> {
    return /* NEW {{name}} HERE */
  }

  {{/projections}}
}
`
