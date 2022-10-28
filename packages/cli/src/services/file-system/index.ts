import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

export class FileSystemError {
  readonly _tag = 'FileSystemError'
  readonly error: Error
  public constructor(readonly reason: unknown) {
    this.error = reason instanceof Error ? reason : new Error(JSON.stringify(reason))
  }
}

export interface FileSystemService {
  readonly readDirectoryContents: (directoryPath: string) => Effect<unknown, FileSystemError, ReadonlyArray<string>>
}

export const FileSystemService = tag<FileSystemService>()
