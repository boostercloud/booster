import { Hook } from '@oclif/core'
import path = require('path')
import * as process from 'process'
import { logger } from '../../services/logger'
import { initializeEnvironment } from '../../services/environment'

// Function to find and remove the `-e` argument and its accompanying value
const extractEnvironmentArg = (): string | undefined => {
  const argv = process.argv
  const eIndex = argv.indexOf('-e') // Find the index of `-e` in argv
  let environment // Variable to store the value found after `-e`

  if (eIndex !== -1 && eIndex + 1 < argv.length) {
    environment = argv[eIndex + 1] // Save the value following `-e`
    argv.splice(eIndex, 2) // Remove both `-e` and its value from argv
  }
  return environment
}

const hook: Hook<'command_not_found'> = async function (opts) {
  // ensure opts.argv is argv without the environment
  process.env['BOOSTER_CLI_HOOK'] = 'true'
  if (opts.id === '-e') {
    opts.id = ''
    opts.argv = ['-e', ...(opts.argv ?? [])]
  }
  const firstArgs = process.argv.filter((arg) => !opts.argv?.includes(arg))
  const environment = extractEnvironmentArg()
  opts.argv = process.argv.filter((arg) => !firstArgs.includes(arg))
  const cwd = process.cwd()

  if (initializeEnvironment(logger, environment)) {
    // to allow doing `boost -e <env>` and show the help
    if (!opts.id && opts.argv.length === 0) {
      ;(process.argv as unknown) = [...firstArgs, '--help']
    }
    await import(path.join(cwd, 'dist', 'index.js'))
  }
}

export default hook
