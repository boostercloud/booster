export const template = `export class {{{ name }}} {
  public constructor(
    {{#fields}}
    public {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}
}
`
