import Command from '@oclif/command'
import { checkCurrentDirBoosterVersion } from '../services/project-checker'
import { appendOnErrorsFile } from '../services/logger'

export default abstract class BaseCommand extends Command {
  async init() {
    await checkCurrentDirBoosterVersion(this.config.userAgent)
  }

  async catch(fullError: any) {
    const errorMessage = fullError.message.split("\n")[0].replace('Error:','')
    const logRefMessage = "\n(You can see the full error logs in ./errors.log)"
    console.log("BASECOMMAND")
    console.log(new Date().toISOString())
    console.log(fullError)
    console.log("BASECOMMAND")
    await appendOnErrorsFile(fullError.message)
    return super.catch(new Error(errorMessage + logRefMessage))
  }
}
