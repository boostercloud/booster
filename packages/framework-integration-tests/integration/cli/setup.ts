import { exec } from 'child_process'

before(async () => {
  await removeFiles()
})

after(async () => {
  await exec('npm run compile')
  await removeFiles()
})

async function removeFiles(): Promise<void> {
  await exec('rm src/entities/Post.ts')
  return
}
