import { Logger } from '@boostercloud/framework-types'

export class MyLogger implements Logger {
  debug(message?: any, ...optionalParams: any[]): void {
    console.log(`[DEBUG] ${message?.toString()}`)
    optionalParams.forEach((param) => {
      console.log(`[DEBUG] ${param?.toString()}`)
    })
  }

  info(message?: any, ...optionalParams: any[]): void {
    console.log(`[INFO] ${message?.toString()}`)
    optionalParams.forEach((param) => {
      console.log(`[INFO] ${param?.toString()}`)
    })
  }

  warn(message?: any, ...optionalParams: any[]): void {
    console.log(`[WARN] ${message?.toString()}`)
    optionalParams.forEach((param) => {
      console.log(`[WARN] ${param?.toString()}`)
    })
  }

  error(message?: any, ...optionalParams: any[]): void {
    console.log(`[ERROR] ${message?.toString()}`)
    optionalParams.forEach((param) => {
      console.log(`[ERROR] ${param?.toString()}`)
    })
  }
}
