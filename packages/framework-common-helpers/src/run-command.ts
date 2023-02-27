import { spawn, ChildProcessWithoutNullStreams } from 'child_process'

/** Spawn a CLI command and optionally printing the logs and error messages */
export function runCommandAsync(path: string, command: string, ignoreLogs = false): ChildProcessWithoutNullStreams {
  // Split the command into an array of arguments
  const args = command.split(' ')

  // Use the first argument as the command name
  const subprocess = spawn(args[0], args.slice(1), {
    cwd: path,
    stdio: 'pipe',
  })

  if (!ignoreLogs) {
    subprocess.stdout.on('data', (data) => {
      console.log(data.toString())
    })

    subprocess.stderr.on('data', (data) => {
      console.error(data.toString())
    })
  }

  return subprocess
}

/** Synchronously run a CLI command and return the stdout, optionally printing the logs and error messages */
export function runCommand(path: string, command: string, ignoreLogs = false): Promise<string> {
  return new Promise((resolve, reject) => {
    const subprocess = runCommandAsync(path, command, ignoreLogs)
    let stdout = ''
    let stderr = ''

    if (subprocess) {
      subprocess.stdout.on('data', (data) => {
        stdout += data.toString()
      })

      subprocess.stderr.on('data', (data) => {
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
