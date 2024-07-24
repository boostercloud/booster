"use strict";
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const schema_migrator_1 = require("../src/schema-migrator");
class TestConceptV1 {
    constructor(field1) {
        this.field1 = field1;
    }
}
class TestConceptV2 {
    constructor(field1, field2) {
        this.field1 = field1;
        this.field2 = field2;
    }
}
class TestConceptV3 {
    constructor(field1, field2, field3) {
        this.field1 = field1;
        this.field2 = field2;
        this.field3 = field3;
    }
}
class TestConceptMigration {
    addField2(old) {
        return new TestConceptV2(old.field1, 2);
    }
    addField3(old) {
        return new TestConceptV3(old.field1, old.field2, 'default');
    }
}
describe('Schema Migrator', () => {
    const schemaMigrations = new Map();
    schemaMigrations.set(2, {
        fromSchema: TestConceptV1,
        toSchema: TestConceptV2,
        methodName: 'addField2',
        migrationClass: TestConceptMigration,
        toVersion: 2,
    });
    schemaMigrations.set(3, {
        fromSchema: TestConceptV2,
        toSchema: TestConceptV3,
        methodName: 'addField3',
        migrationClass: TestConceptMigration,
        toVersion: 3,
    });
    const config = new framework_types_1.BoosterConfig('test');
    config.schemaMigrations['TestConcept'] = schemaMigrations;
    const migrator = new schema_migrator_1.SchemaMigrator(config);
    describe('schema migrate', async () => {
        it('throws when the version of the concept to migrate is lower than 1', async () => {
            const toMigrate = {
                requestID: 'requestID',
                typeName: 'TestConcept',
                version: 0,
                value: {},
            };
            await (0, expect_1.expect)(migrator.migrate(toMigrate)).to.be.rejectedWith(/Received an invalid schema version value, 0, for TestConcept/);
        });
        it('throws when the version of the concept to migrate is higher than the current version', async () => {
            const toMigrate = {
                requestID: 'requestID',
                typeName: 'TestConcept',
                version: 4,
                value: {},
            };
            await (0, expect_1.expect)(migrator.migrate(toMigrate)).to.be.rejectedWith(/The current schema version of TestConcept is 3, which is lower than the received version 4/);
        });
        it('does not migrate when the received version is the same as the current version', async () => {
            const toMigrate = {
                requestID: 'requestID',
                typeName: 'TestConcept',
                version: 3,
                value: {},
            };
            (0, expect_1.expect)(await migrator.migrate(toMigrate)).to.equal(toMigrate);
        });
        it('migrates when the received version is lower than the current one', async () => {
            const toMigrate = {
                requestID: 'requestID',
                typeName: 'TestConcept',
                version: 1,
                value: {
                    field1: 'test-field1',
                },
            };
            const expected = {
                requestID: 'requestID',
                typeName: 'TestConcept',
                version: 3,
                value: {
                    field1: 'test-field1',
                    field2: 2,
                    field3: 'default',
                },
            };
            const got = (await migrator.migrate(toMigrate));
            const value = got.value;
            (0, expect_1.expect)(got).not.to.be.equal(toMigrate); // This checks the reference is not the same (i.e. a different object is returned)
            (0, expect_1.expect)(got).to.be.deep.equal(expected);
            (0, expect_1.expect)(value.constructor.name).to.be.equal('TestConceptV3');
        });
    });
});
