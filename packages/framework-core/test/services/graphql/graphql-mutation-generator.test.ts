/* eslint-disable @typescript-eslint/no-explicit-any */
import { CommandMetadata, UUID } from '@boostercloud/framework-types'
import { GraphQLInputObjectType } from 'graphql'
import { fake } from 'sinon'
import { GraphQLMutationGenerator } from '../../../src/services/graphql/graphql-mutation-generator'
import { GraphQLTypeInformer } from '../../../src/services/graphql/graphql-type-informer'
import { expect } from '../../expect'

describe('GraphQLMutationGenerator', () => {
  context('with a valid config', () => {
    class SomeCommand {
      public constructor() {}
      public static async handle(): Promise<any> {}
    }

    const commandHandlers: Record<string, CommandMetadata> = {
      SomeCommand: {
        class: SomeCommand,
        properties: [
          {
            name: 'id',
            type: UUID,
          },
          {
            name: 'name',
            type: String,
          },
        ],
        authorizedRoles: 'all',
      },
    }

    const typeInformer = new GraphQLTypeInformer({ ...commandHandlers })

    describe('the `generate` method', () => {
      it('generates the corresponding mutations', () => {
        const mutationsSchema = new GraphQLMutationGenerator(commandHandlers, typeInformer, fake()).generate()

        expect(mutationsSchema).not.to.be.null
        const mutations = mutationsSchema.getFields()
        const someCommandMutation = mutations['SomeCommand'].args[0] // Should be the only one
        expect(someCommandMutation.name).to.equal('input')
        const inputType = (someCommandMutation.type as any).ofType as GraphQLInputObjectType
        expect(inputType.name).to.equal('SomeCommandInput')
        const attributes = inputType.getFields()
        expect(attributes['id'].name).to.equal('id')
        expect(attributes['id'].type).to.be.a('GraphQLScalarType')
        expect(attributes['id'].type['name']).to.equal('ID')
        expect(attributes['name'].name).to.equal('name')
        expect(attributes['name'].type).to.be.a('GraphQLScalarType')
        expect(attributes['name'].type['name']).to.equal('String')
      })
    })
  })
})
