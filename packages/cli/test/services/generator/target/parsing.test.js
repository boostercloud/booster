"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const parsing_1 = require("../../../../src/services/generator/target/parsing");
const expect_1 = require("../../../expect");
describe('parsing', () => {
    describe('parseFields', () => {
        it('one correct field', async () => {
            const fields = await (0, parsing_1.parseFields)(['title:string']);
            (0, expect_1.expect)(fields.fields).to.have.lengthOf(1);
            (0, expect_1.expect)(fields.fields[0].name).to.equal('title');
            (0, expect_1.expect)(fields.fields[0].type).to.equal('string');
        });
        it('many correct fields', async () => {
            const fields = await (0, parsing_1.parseFields)(['name:string', 'age:number', 'id:UUID']);
            (0, expect_1.expect)(fields.fields).to.have.lengthOf(3);
            (0, expect_1.expect)(fields.fields[0].name).to.equal('name');
            (0, expect_1.expect)(fields.fields[0].type).to.equal('string');
            (0, expect_1.expect)(fields.fields[1].name).to.equal('age');
            (0, expect_1.expect)(fields.fields[1].type).to.equal('number');
            (0, expect_1.expect)(fields.fields[2].name).to.equal('id');
            (0, expect_1.expect)(fields.fields[2].type).to.equal('UUID');
        });
        it('one field without type', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
        });
        it('many fields without type', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field content');
        });
        it('one field with empty type', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
        });
        it('many fields with empty type', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content:']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field content');
        });
        it('one field with empty name', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)([':string']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field :string');
        });
        it('many fields with empty name', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', ':string']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field :string');
        });
        it('duplicated fields', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content:string', 'title:number']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Fields cannot be duplicated');
        });
        it('many duplicated fields', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content:string', 'title:number', 'content:string']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Fields cannot be duplicated');
        });
        it('field without type and duplicated fields', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content', 'title:number']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field content');
        });
        it('duplicated fields and a field without type', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseFields)(['title:string', 'content:string', 'title:number', 'category']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field category');
        });
    });
    describe('parseProjections', () => {
        it('one correct entity and id', async () => {
            const projections = await (0, parsing_1.parseProjections)(['Post:id']);
            (0, expect_1.expect)(projections.projections).to.have.lengthOf(1);
            (0, expect_1.expect)(projections.projections[0].entityName).to.equal('Post');
            (0, expect_1.expect)(projections.projections[0].entityId).to.equal('id');
        });
        it('many correct entities and ids', async () => {
            const projections = await (0, parsing_1.parseProjections)(['Post:id', 'Comment:id']);
            (0, expect_1.expect)(projections.projections).to.have.lengthOf(2);
            (0, expect_1.expect)(projections.projections[0].entityName).to.equal('Post');
            (0, expect_1.expect)(projections.projections[0].entityId).to.equal('id');
            (0, expect_1.expect)(projections.projections[1].entityName).to.equal('Comment');
            (0, expect_1.expect)(projections.projections[1].entityId).to.equal('id');
        });
        it('one entity without id', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)(['Post']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post');
        });
        it('many entities without id', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)(['Post:id', 'Comment']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Comment');
        });
        it('one entity with empty id', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)(['Post:']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post:');
        });
        it('many entities with empty id', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)(['Post:id', 'Comment:']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Comment:');
        });
        it('one entity with empty name', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)([':id']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection :id');
        });
        it('many entities with empty name', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjections)(['Post:id', ':id']);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection :id');
        });
    });
    describe('parseProjectionField', () => {
        it('correct entity with id', async () => {
            const projection = await (0, parsing_1.parseProjectionField)('Post:id');
            (0, expect_1.expect)(projection.projection.entityName).to.equal('Post');
            (0, expect_1.expect)(projection.projection.entityId).to.equal('id');
        });
        it('entity without id', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjectionField)('Post');
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post');
        });
        it('entity with empty name', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await (0, parsing_1.parseProjectionField)(':id');
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection :id');
        });
    });
});
