import { Dirent } from 'fs'

/**
 * Abstract File System service, implementations must handle path normalization and
 * everything related to accessing the filesystem in a cross-platform and cloud-agnostic way.
 */
export abstract class FileSystem {
  /**
   * Read the contents of a directory
   */
  abstract readDirectoryContents(directoryPath: string): Promise<ReadonlyArray<Dirent>>

  /**
   * Read the contents of a file
   */
  abstract readFileContents(filePath: string): Promise<string>

  /**
   * Checks if a file or directory exists
   */
  abstract exists(filePath: string): Promise<boolean>

  /**
   * Removes a file or directory
   */
  abstract remove(path: string, options: { recursive?: boolean; force?: boolean }): Promise<void>

  /**
   * Makes a directory
   */
  abstract makeDirectory(path: string, options: { recursive?: boolean }): Promise<void>

  /**
   * Copies a file or directory
   */
  abstract copy(source: string, destination: string): Promise<void>

  /**
   * Writes a file, creating the directory if it doesn't exist
   */
  abstract outputFile(path: string, contents: string): Promise<void>
}
