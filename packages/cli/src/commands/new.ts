import { Command, flags } from '@oclif/core'
import { ProjectChecker } from '../services/project-checker'
import { Script } from '../services/script'
import Brand from '../services/brand'
import { templates } from '../templates'

export default class NewScheduledCommand extends Command {
  public static description = 'create a new scheduled command'
  public static flags = {
    help: flags.help({ char: 'h' }),
  }
  public static args = [
    { name: 'commandName', required: true, description: 'name of the command' },
    { name: 'cronParams', required: true, description: 'cron parameters for the command' },
  ]

  public async run(): Promise<void> {
    const { args } = this.parse(NewScheduledCommand)
    const commandName = args.commandName
    const cronParams = args.cronParams.split(' ')
    Script.init('new:scheduled-command', [commandName, ...cronParams])
    ProjectChecker.check(this)
    Brand.showAsciiLogo()
    templates.newScheduledCommand(commandName, cronParams)
  }
}

export function newScheduledCommand(name: string, cronParams: string[]): void {
  const fields = ['minute', 'hour', 'day', 'month', 'weekDay', 'year']
  const cronParamsObject = fields.reduce((obj, field, index) => {
    obj[field] = cronParams[index] || '*'
    return obj
  }, {} as Record<string, string>)

  const scheduledCommandCode = `
    @ScheduledCommand({
      ${Object.entries(cronParamsObject)
        .map(([key, value]) => `${key}: '${value}',`)
        .join('\n')}
    })
    export class ${name} {
      public handle(register: Register): void {
        register.events([])
      }
    }
  `
  fs.writeFileSync(`src/commands/${name}.ts`, scheduledCommandCode)
}
