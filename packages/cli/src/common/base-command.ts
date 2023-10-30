import { Command } from '@oclif/core'
import { checkCurrentDirBoosterVersion } from '../services/project-checker'
import { appendOnErrorsFile } from '../services/logger'

export default abstract class BaseCommand extends Command {
  async init() {
    await checkCurrentDirBoosterVersion(this.config.version)
  }

  async catch(fullError: Error) {
    const errorMessage = fullError.message.split('\n')[0].replace('Error:', '')
    const logRefMessage = '\n(You can see the full error logs in ./errors.log)'
    const errorForFile = `\nboost ${this.id} ${this.argv.join(' ')}\n${fullError.message}`
    appendOnErrorsFile(errorForFile)
    return super.catch(new Error(errorMessage + logRefMessage))
  }
}
