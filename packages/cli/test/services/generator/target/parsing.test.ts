import { parseFields, parseProjections } from '../../../../src/services/generator/target/parsing'
import { expect } from '../../../expect'

describe('parsing',() => {

    describe('parseFields', () => {

        it('one correct field', async () => {
            const fields = await parseFields(['title:string'])
            expect(fields.fields).to.have.lengthOf(1)
            expect(fields.fields[0].name).to.equal('title')
            expect(fields.fields[0].type).to.equal('string')
        })

        it('many correct fields', async () => {
            const fields = await parseFields(['name:string','age:number','id:UUID'])
            expect(fields.fields).to.have.lengthOf(3)
            expect(fields.fields[0].name).to.equal('name')
            expect(fields.fields[0].type).to.equal('string')
            expect(fields.fields[1].name).to.equal('age')
            expect(fields.fields[1].type).to.equal('number')
            expect(fields.fields[2].name).to.equal('id')
            expect(fields.fields[2].type).to.equal('UUID')
        })

        it('one field without type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field title'
            )
        })

        it('many fields without type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content'
            )
        })

        it('one field with empty type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field title'
            )
        })

        it('many fields with empty type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content'
            )
        })
        
        it('one field with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields([':string'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field :string'
            )
        })

        it('many fields with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string',':string'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field :string'
            )
        })

        it('duplicated fields', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:string','title:number'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Fields cannot be duplicated'
            )
        })

        it('many duplicated fields', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:string','title:number','content:string'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Fields cannot be duplicated'
            )
        })

        it('field without type and duplicated fields', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content','title:number'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content'
            )
        })

        it('duplicated fields and a field without type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:string','title:number','category'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field category'
            )
        })
    })

    describe('parseProjections', () => {

        it('one correct entity and id', async () => {
            const projections = await parseProjections(['Post:id'])
            expect(projections.projections).to.have.lengthOf(1)
            expect(projections.projections[0].entityName).to.equal('Post')
            expect(projections.projections[0].entityId).to.equal('id')
        })

        it('many correct entities and ids', async () => {
            const projections = await parseProjections(['Post:id','Comment:id'])
            expect(projections.projections).to.have.lengthOf(2)
            expect(projections.projections[0].entityName).to.equal('Post')
            expect(projections.projections[0].entityId).to.equal('id')
            expect(projections.projections[1].entityName).to.equal('Comment')
            expect(projections.projections[1].entityId).to.equal('id')
        })

        it('one entity without id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Post'
            )
        })

        it('many entities without id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id','Comment'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Comment'
            )
        })

        it('one entity with empty id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Post:'
            )
        })

        it('many entities with empty id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id','Comment:'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Comment:'
            )
        })
        
        it('one entity with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections([':id'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection :id'
            )
        })

        it('many entities with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id',':id'])
            } catch (err) {
                const e = err as Error
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection :id'
            )
        })

    })
})