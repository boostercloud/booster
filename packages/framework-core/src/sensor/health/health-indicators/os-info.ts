import * as os from 'os'

export interface OsInfoCpuResult {
  cpu: os.CpuInfo
  timesPercentages: Array<number>
}

export interface OsInfoMemoryResult {
  totalBytes: number
  freeBytes: number
}

export interface OsInfoResult {
  cpus: Array<OsInfoCpuResult>
  memory: OsInfoMemoryResult
}

export async function osInfo(): Promise<OsInfoResult> {
  const cpus = os.cpus()
  const cpuResult = cpus.map((cpu: os.CpuInfo) => {
    // times is an object containing the number of CPU ticks spent in: user, nice, sys, idle, and irq
    const totalTimes = Object.values(cpu.times).reduce((accumulator, value) => {
      return accumulator + value
    }, 0)
    const timesPercentages = Object.values(cpu.times).map((time) => {
      return Math.round((100 * time) / totalTimes)
    })
    return {
      cpu,
      timesPercentages,
    }
  })

  return {
    cpus: cpuResult,
    memory: {
      totalBytes: os.totalmem(),
      freeBytes: os.freemem(),
    },
  }
}
