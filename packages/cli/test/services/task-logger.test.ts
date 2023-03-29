import { expect } from '../expect'
import { fake } from 'sinon'
import { OraTaskLogger } from '../../src/services/task-logger/ora-task-logger'
import { NoOpTaskLogger } from '../../src/services/task-logger/noop-task-logger'

describe('TaskLogger', () => {
  describe('- Implementation: OraTaskLogger', () => {
    const sut = new OraTaskLogger()
    const mockOra = {
      start: fake(),
      succeed: fake(),
      fail: fake(),
    }

    beforeEach(() => {
      Object.values(mockOra).forEach((mock) => mock.resetHistory())
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(sut as any).oraLogger = mockOra
    })

    describe('logTask method', () => {
      it('should call start, succeed, and return the result on success', async () => {
        const msg = 'Some task'
        const task = fake(() => 'result')
        const result = await sut.logTask(msg, task)
        expect(task).to.have.been.called
        expect(mockOra.start).to.have.been.called.with(msg)
        expect(mockOra.succeed).to.have.been.called.with(msg)
        expect(result).to.be('result')
      })

      it('should call fail and throw an error on failure', async () => {
        const msg = 'Some task'
        const task = fake(() => {
          throw new Error('Task failed')
        })
        await expect(sut.logTask(msg, task)).to.be.rejectedWith('Task failed')
        expect(task).to.have.been.called
        expect(mockOra.start).to.have.been.calledWith(msg)
        expect(mockOra.fail).to.have.been.calledWith(msg)
      })
    })
  })

  describe('- Implementation: NoOpTaskLogger', () => {
    const sut = new NoOpTaskLogger()

    describe('logTask method', () => {
      it('should return the result on success', async () => {
        const msg = 'Some task'
        const task = fake(() => 'result')
        const result = await sut.logTask(msg, task)
        expect(task).to.have.been.called
        expect(result).to.be('result')
      })

      it('should throw an error on failure', async () => {
        const msg = 'Some task'
        const task = fake(() => {
          throw new Error('Task failed')
        })
        await expect(sut.logTask(msg, task)).to.be.rejectedWith('Task failed')
        expect(task).to.have.been.called
      })
    })
  })
})
