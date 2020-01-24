/**
 * Builds an error extracting its message from the "stdout" and "stderr" properties if present
 * @param e
 * @param prefix
 */
export function wrapExecError(e: Error, prefix: string): Error {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { stdout, stderr } = e as any
  return new Error(prefix + '\n' + stdout + stderr)
}
