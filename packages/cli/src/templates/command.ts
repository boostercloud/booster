export const template = `import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone 
})
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  public handle(register: Register): void {
    register.events( /* YOUR EVENT HERE */)
  }
}
`
