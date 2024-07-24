import { FakeOverrides } from '@boostercloud/application-tester/src/effect';
import { PackageManagerService } from '../../../src/services/package-manager';
export declare const makeTestPackageManager: (overrides?: FakeOverrides<PackageManagerService>) => import("@boostercloud/application-tester/src/effect").FakeServiceUtils<PackageManagerService>;
