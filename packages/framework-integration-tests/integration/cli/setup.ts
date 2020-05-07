import util = require('util')
const exec = util.promisify(require('child_process').exec)

before(async () => {
  await removeFiles()
})

after(async () => {
  await exec('npm run compile')
  await removeFiles()
})

async function removeFiles(): void {
  await exec('rm src/entities/Post.ts')
  return
}
