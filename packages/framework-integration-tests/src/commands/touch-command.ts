import { BoosterTouchEntityHandler, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class TouchCommand {
  public constructor() {}

  public static async handle(_command: TouchCommand, _register: Register): Promise<void> {
    console.log('touch entity started')
    await BoosterTouchEntityHandler.run()
    console.log('touch entity finished')
  }
}
