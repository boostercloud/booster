/* eslint-disable @typescript-eslint/no-explicit-any */
import { Layer, succeedWith } from '@boostercloud/framework-types/src/effect'
import { FileSystemService } from '../../../src/services/file-system'
import { fake, SinonSpy } from 'sinon'

type Overrides = Record<keyof FileSystemService, SinonSpy<any[], any>>

export const TestFileSystem = (overrides?: Overrides) => {
  const defaultFakes = {
    readDirectoryContents: fake.returns([]),
  } as Overrides
  const fakes = { ...defaultFakes, ...overrides }
  const layer = Layer.fromValue(FileSystemService)({
    readDirectoryContents: (path) => succeedWith(() => fakes.readDirectoryContents(path)),
  })
  return { layer, fakes }
}
