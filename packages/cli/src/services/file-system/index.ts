import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

export class ReadDirectoryContentsError {
  readonly _tag = 'ReadDirectoryContentsError'
  constructor(readonly error: Error) {}
}

export interface FileSystemService {
  readonly readDirectoryContents: (
    directoryPath: string
  ) => Effect<unknown, ReadDirectoryContentsError, ReadonlyArray<string>>
}

export const FileSystemService = tag<FileSystemService>()
