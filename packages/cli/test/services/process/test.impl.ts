/* eslint-disable @typescript-eslint/no-explicit-any */
import { Layer, succeedWith } from '@boostercloud/framework-types/src/effect'
import { ProcessService } from '../../../src/services/process'
import { fake, SinonSpy } from 'sinon'

type Overrides = {
  [key in keyof ProcessService]?: SinonSpy<any[], any>
}

export const TestProcess = (overrides?: Overrides) => {
  const defaultFakes = { cwd: fake.returns(''), exec: fake.returns('') } as Required<Overrides>
  const fakes = { ...defaultFakes, ...overrides }
  const layer = Layer.fromValue(ProcessService)({
    cwd: () => succeedWith(() => fakes.cwd()),
    exec: (command: string, cwd?: string) => succeedWith(() => fakes.exec(command, cwd)),
  })
  return { layer, fakes }
}
