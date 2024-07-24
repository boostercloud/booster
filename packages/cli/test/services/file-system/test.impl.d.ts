import { FileSystemService } from '../../../src/services/file-system';
import { FakeOverrides } from '@boostercloud/application-tester/src/effect';
export declare const makeTestFileSystem: (overrides?: FakeOverrides<FileSystemService>) => import("@boostercloud/application-tester/src/effect").FakeServiceUtils<FileSystemService>;
