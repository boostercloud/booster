export function withinWorkingDirectory<TReturn>(workingDirectory: string, actions: () => TReturn): TReturn {
  const currentWorkingDirectory = process.cwd()
  process.chdir(workingDirectory)
  const result = actions()
  process.chdir(currentWorkingDirectory)
  return result
}
