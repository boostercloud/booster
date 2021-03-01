import Command from '@oclif/command'
import { checkCurrentDirBoosterVersion } from '../services/project-checker'

export default abstract class BaseCommand extends Command {
  async init() {
    await checkCurrentDirBoosterVersion(this.config.userAgent)
  }

  async catch(err: any) {
    // add any custom logic to handle errors from the command
    // or simply return the parent class error handling
    return super.catch(err)
  }
}
