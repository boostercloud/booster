export type BackupStackParams = {
  backupType: string
  onDemandBackupRules?: OnDemandBackupRules
  pointInTimeRules?: PointInTimeRules
}

// There are more rules that we could add in the future
export type OnDemandBackupRules = {
  minute?: string
  hour?: string
  day?: string
  month?: string
  weekDay?: string
  year?: string
}

export type PointInTimeRules = {
  generateRestoreAPI: boolean
}
