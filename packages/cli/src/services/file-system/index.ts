import { deriveLifted, Effect, tag } from '@boostercloud/framework-types/src/effect'

export class FileSystemError {
  readonly _tag = 'FileSystemError'
  readonly error: Error
  public constructor(readonly reason: unknown) {
    this.error = reason instanceof Error ? reason : new Error(String(reason))
  }
}

export interface FileSystemService {
  readonly readDirectoryContents: (directoryPath: string) => Effect<unknown, FileSystemError, ReadonlyArray<string>>
}

export const FileSystemService = tag<FileSystemService>()

/**
 * Helper SDK to be able to run service methods outside of the layers
 */
export const fileSystemInternals = deriveLifted(FileSystemService)(
  // Functions to export from the service
  ['readDirectoryContents'],
  // Constants to export from the service
  [],
  // Values returned from side effects in the service
  []
)
