import { Layer, succeed, succeedWith } from '@boostercloud/framework-types/src/effect'
import { PackageManagerService } from '../../../src/services/package-manager'

export const TestPackageManager = Layer.fromValue(PackageManagerService)({
  runScript: () => succeed(''),
  installAllDependencies: () => succeedWith(() => {}),
  installProductionDependencies: () => succeedWith(() => {}),
  setProjectRoot: () => succeedWith(() => {}),
})
