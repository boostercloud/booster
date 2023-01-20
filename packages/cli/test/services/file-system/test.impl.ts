import { FileSystemService } from '../../../src/services/file-system'
import { fake } from 'sinon'
import { FakeOverrides, fakeService } from '@boostercloud/application-tester/src/effect'

export const makeTestFileSystem = (overrides?: FakeOverrides<FileSystemService>) =>
  fakeService(FileSystemService, {
    readDirectoryContents: fake.returns([]),
    ...overrides,
  })
