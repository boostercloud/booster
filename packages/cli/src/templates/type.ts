export const template = `export class {{{ name }}} {
  {{#fields}}
  {{{name}}}: {{{type}}}
  {{/fields}}
}
`
