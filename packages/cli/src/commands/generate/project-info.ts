import { flags } from '@oclif/command'
import { writeFile } from 'fs'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import { compileProjectAndLoadConfig } from '../../services/config-service'
import { initializeEnvironment } from '../../services/environment'
import { logger } from '../../services/logger'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'

const runTasks = async (): Promise<void> =>
  Script.init('boost genereate:project-info', Promise.resolve(process.cwd()))
    .step('Checking project structure', checkCurrentDirIsABoosterProject)
    .step('Generating project info', async () => {
      const projectConfig = await compileProjectAndLoadConfig(process.cwd())
      writeFile('dist/project-info.json', JSON.stringify(projectConfig, null, 2), console.error)
    })
    .info('Project info generated! You can find it in the "dist" folder')
    .done()

export default class ProjectInfo extends BaseCommand {
  public static description = 'Generate the current project Booster config information'

  public static flags = {
    help: flags.help({ char: 'h' }),
    verbose: flags.boolean({
      description: 'display full error messages',
      default: false,
    }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
      default: 'local',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(ProjectInfo)

    if (initializeEnvironment(logger, flags.environment)) {
      await runTasks()
    }
  }

  async catch(fullError: Error) {
    const {
      flags: { verbose },
    } = this.parse(ProjectInfo)

    if (verbose) {
      console.error(fullError.message)
    }

    return super.catch(fullError)
  }
}
