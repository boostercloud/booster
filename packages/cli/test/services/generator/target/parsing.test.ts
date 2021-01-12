import { parseFields } from '../../../../src/services/generator/target/parsing'
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
    })

})