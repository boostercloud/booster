import { spawn, ChildProcessWithoutNullStreams } from 'child_process'

function spawnCommand(path: string, command: string): ChildProcessWithoutNullStreams {
  // Split the command into an array of arguments
  const args = command.split(' ')

  // Use the first argument as the command name
  const subprocess = spawn(args[0], args.slice(1), {
    cwd: path,
    stdio: 'pipe',
  })

  return subprocess
}

export function runCommand(path: string, command: string, ignoreLogs = false): Promise<string> {
  return new Promise((resolve, reject) => {
    const subprocess = spawnCommand(path, command)
    let stdout = ''
    let stderr = ''

    if (subprocess) {
      subprocess.stdout.on('data', (data) => {
        if (!ignoreLogs) console.log(data.toString())
        stdout += data.toString()
      })

      subprocess.stderr.on('data', (data) => {
        if (!ignoreLogs) console.error(data.toString())
        stderr += data.toString()
      })

      subprocess.on('close', (code) => {
        if (code === 0) {
          resolve(stdout)
        } else {
          reject(stderr)
        }
      })
    }
  })
}
