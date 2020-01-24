export const template = `import { Entity, ReactsTo } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Entity
export class {{{name}}} {
  public constructor(
    public id: UUID,
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  {{#events}}
  @ReactsTo({{{eventName}}})
  public static project{{{eventName}}}(event: {{{eventName}}}, current{{{name}}}?: {{{name}}}): {{{name}}} {
    return /* NEW {{name}} HERE */
  }

  {{/events}}
}
`
