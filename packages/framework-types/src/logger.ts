export enum Level {
  debug = 0,
  info = 1,
  warn = 2,
  error = 3,
}

export interface Logger {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  debug(message?: any, ...optionalParams: any[]): void
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  info(message?: any, ...optionalParams: any[]): void
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  warn(message?: any, ...optionalParams: any[]): void
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  error(message?: any, ...optionalParams: any[]): void
}
