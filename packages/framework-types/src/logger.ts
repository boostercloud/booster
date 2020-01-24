export enum Level {
  // eslint-disable-next-line @typescript-eslint/no-magic-numbers
  debug = 0,
  info,
  error,
}

export interface Logger {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  debug(message?: any, ...optionalParams: any[]): void
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  info(message?: any, ...optionalParams: any[]): void
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  error(message?: any, ...optionalParams: any[]): void
}
