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
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field title. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('many fields without type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('one field with empty type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field title:. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('many fields with empty type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content:. Fields must be in the form of <field name>:<field type>'
            )
        })
        
        it('one field with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields([':string'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field :string. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('many fields with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string',':string'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field :string. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('duplicated fields', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:string','title:number'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field title. Fields cannot be duplicated'
            )
        })

        it('field without type and duplicated fields', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content','title:number'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field content. Fields must be in the form of <field name>:<field type>'
            )
        })

        it('duplicated fields and a field without type', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseFields(['title:string','content:string','title:number','category'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing field category. Fields must be in the form of <field name>:<field type>'
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
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>'
            )
        })

        it('many entities without id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id','Comment'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Comment. Projections must be in the form of <entity name>:<entity id>'
            )
        })

        it('one entity with empty id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Post:. Projections must be in the form of <entity name>:<entity id>'
            )
        })

        it('many entities with empty id', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id','Comment:'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection Comment:. Projections must be in the form of <entity name>:<entity id>'
            )
        })
        
        it('one entity with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections([':id'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>'
            )
        })

        it('many entities with empty name', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
                await parseProjections(['Post:id',':id'])
            } catch (e) {
                exceptionThrown = true
                exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain(
                'Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>'
            )
        })

    })
})