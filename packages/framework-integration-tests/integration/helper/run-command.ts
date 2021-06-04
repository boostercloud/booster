import { exec, spawn, ChildProcessPromise, PromiseResult } from 'child-process-promise'

export function runCommand(
  path: string,
  command: string,
  ignoreLogs = false
): ChildProcessPromise<PromiseResult<string>> {
  const subprocess = exec(command, {
    cwd: path, // Commands are run in the integration tests package root
  })

  if (!ignoreLogs && subprocess.childProcess && process) {
    // Redirect process chatter to make it look like it's doing something
    subprocess.childProcess?.stdout?.pipe(process.stdout)
    subprocess.childProcess?.stderr?.pipe(process.stderr)
  }

  return subprocess
}

export function runCommandBackground(
  path: string,
  command: string,
  ignoreLogs = false
): ChildProcessPromise<PromiseResult<string>> {
  console.log("spawn command: " + command)
  const subprocess = spawn(command, [], {
    cwd: path, // Commands are run in the integration tests package root
    detached: true,
  })

  if (!ignoreLogs && subprocess.childProcess && process) {
    // Redirect process chatter to make it look like it's doing something
    subprocess.childProcess?.stdout?.pipe(process.stdout)
    subprocess.childProcess?.stderr?.pipe(process.stderr)
  }

  return subprocess
}