export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

@ScheduledCommand({
  // Specify cron scheduled params here. By default scheduledCommand will be triggered every 30 minutes
  minute: '0/30'
})
export class {{{ name }}} {
  public constructor(
    {{#fields}}
    readonly {{{name}}}: {{{type}}},
    {{/fields}}
  ) {}

  public static async handle(command: {{{ name }}} , register: Register): Promise<void> {
    register.events( /* YOUR EVENT HERE */)
  }
}
`
