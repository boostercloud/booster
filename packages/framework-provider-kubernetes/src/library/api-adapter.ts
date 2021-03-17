/* eslint-disable @typescript-eslint/no-unused-vars */
export const requestSucceeded = (_body?: unknown): Promise<unknown> => {
  throw new Error('apiAdapter#requestSucceeded: Not implemented')
}

export const requestFailed = (_error: Error): Promise<unknown> => {
  throw new Error('apiAdapter#requestFailed: Not implemented')
}
