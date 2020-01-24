export const template = `export interface {{{ name }}} {
  {{#fields}}
  {{{name}}}: {{{type}}}
  {{/fields}}
}
`
