export const template = `{{#imports}}
import { {{#componentNames}}
  {{.}},{{/componentNames}}
} from '{{{packagePath}}}'
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

  public handle(register: Register): void {
    register.events( /* YOUR EVENT HERE */)
  }
}
`
