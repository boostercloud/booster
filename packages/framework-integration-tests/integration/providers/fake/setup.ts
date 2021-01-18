import { createSandboxProject, removeFolders } from '../../helper/fileHelper'
import { runCommand } from '../../helper/runCommand'
import { sandboxName } from './constants'

let sandboxPath: string

before(async () => {
  console.log('preparing sandboxed project...')
  sandboxPath = await createSandboxProject(sandboxName)
  console.log(`Created sandbox named "${sandboxPath}"`)

  console.log('installing dependencies...')
  await runCommand(sandboxPath, 'npm install')
})

after(async () => {
  console.log('removing sandbox project')
  removeFolders([sandboxPath])
})
