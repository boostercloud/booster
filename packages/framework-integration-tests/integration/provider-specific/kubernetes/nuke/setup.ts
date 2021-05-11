import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { nuke } from '../deploy'
import { removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { sandboxProjectName } from '../constants'
import { exec } from 'child-process-promise'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxProjectName)

  await overrideWithBoosterLocalDependencies(sandboxPath)
  // Only the deploy command creates the production dependencies
  await exec('npm install --production --no-bin-links', { cwd: sandboxPath })

  await nuke(sandboxPath)
  removeSandboxProject(sandboxPath)
})
