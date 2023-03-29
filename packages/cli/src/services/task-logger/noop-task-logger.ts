import { TaskLogger } from '.'
import { LoggerComponent } from '../../common/component'

@LoggerComponent()
export class NoOpTaskLogger implements TaskLogger {
  constructor() {}

  async logTask<T>(_: string, task: () => T): Promise<T> {
    return task()
  }
}
