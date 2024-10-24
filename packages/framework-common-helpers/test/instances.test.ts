/* eslint-disable @typescript-eslint/no-explicit-any */
import { createInstanceWithCalculatedProperties } from '../src'
import { ProjectionFor, ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { expect } from './helpers/expect'
import { random } from 'faker'

describe('the `Instances` helper', () => {
  class PersonReadModel implements ReadModelInterface {
    public constructor(
      readonly id: UUID,
      readonly firstName: string,
      readonly lastName: string,
      readonly friends: Array<PersonReadModel>
    ) {}

    public get fullName(): Promise<string> {
      return Promise.resolve(`${this.firstName} ${this.lastName}`)
    }
  }

  let rawObject: any

  beforeEach(() => {
    rawObject = {
      id: random.uuid(),
      firstName: random.word(),
      lastName: random.word(),
      friends: [
        { id: random.uuid(), firstName: random.word(), lastName: random.word() },
        { id: random.uuid(), firstName: random.word(), lastName: random.word() },
      ],
    }
  })

  describe('the createInstanceWithCalculatedProperties method', () => {
    it('creates an instance of the read model class with the calculated properties included', async () => {
      const propertiesToInclude = ['id', 'fullName'] as ProjectionFor<PersonReadModel>

      const instance = await createInstanceWithCalculatedProperties(PersonReadModel, rawObject, propertiesToInclude)

      expect(instance).to.deep.equal({
        id: rawObject.id,
        fullName: `${rawObject.firstName} ${rawObject.lastName}`,
      })
    })

    it('correctly supports arrays and nested objects in `propertiesToInclude`', async () => {
      const propertiesToInclude = ['id', 'fullName', 'friends[].id'] as ProjectionFor<PersonReadModel>

      const instance = await createInstanceWithCalculatedProperties(PersonReadModel, rawObject, propertiesToInclude)

      expect(instance).to.deep.equal({
        id: rawObject.id,
        fullName: `${rawObject.firstName} ${rawObject.lastName}`,
        friends: [{ id: rawObject.friends[0].id }, { id: rawObject.friends[1].id }],
      })
    })
  })
})
