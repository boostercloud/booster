import { FakeOverrides, fakeService } from '@boostercloud/application-tester/src/effect'
import { fake } from 'sinon'
import { PackageManagerService } from '../../../src/services/package-manager'

export const makeTestPackageManager = (overrides?: FakeOverrides<PackageManagerService>) =>
  fakeService(PackageManagerService, {
    setProjectRoot: fake(),
    runScript: fake.returns(''),
    installAllDependencies: fake(),
    installProductionDependencies: fake(),
    ...overrides,
  })
