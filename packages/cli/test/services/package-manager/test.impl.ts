/* eslint-disable @typescript-eslint/no-explicit-any */
import { Layer, succeedWith } from '@boostercloud/framework-types/src/effect'
import { SinonSpy, fake } from 'sinon'
import { PackageManagerService } from '../../../src/services/package-manager'

type Overrides = Record<keyof PackageManagerService, SinonSpy<any[], any>>

export const TestPackageManager = (overrides?: Overrides) => {
  const defaultFakes = {
    runScript: fake.returns(''),
    installAllDependencies: fake(),
    installProductionDependencies: fake(),
    setProjectRoot: fake(),
  } as Overrides
  const fakes = { ...defaultFakes, ...overrides }
  const layer = Layer.fromValue(PackageManagerService)({
    runScript: (s) => succeedWith(() => fakes.runScript(s)),
    installAllDependencies: () => succeedWith(() => fakes.installAllDependencies()),
    installProductionDependencies: () => succeedWith(() => fakes.installProductionDependencies()),
    setProjectRoot: (s) => succeedWith(() => fakes.setProjectRoot(s)),
  })
  return { layer, fakes }
}
