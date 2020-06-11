import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'

@Command({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone 
})
export class ChangeCart {
  public constructor(
  ) {}

  public async handle(register: Register): Promise<void> {
    register.events( /* YOUR EVENT HERE */)
  }
}
