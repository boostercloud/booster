export const template = `// -> Custom code in the type!
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    public {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}
}
`
