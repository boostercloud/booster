"use strict";
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const read_model_schema_migrator_1 = require("../src/read-model-schema-migrator");
class TestConceptV1 {
    constructor(id, field1) {
        this.id = id;
        this.field1 = field1;
    }
}
class TestConceptV2 {
    constructor(id, field1, field2) {
        this.id = id;
        this.field1 = field1;
        this.field2 = field2;
    }
}
class TestConceptV3 {
    constructor(id, field1, field2, field3) {
        this.id = id;
        this.field1 = field1;
        this.field2 = field2;
        this.field3 = field3;
    }
}
class TestConceptMigration {
    addField2(old) {
        return new TestConceptV2(old.id, old.field1, 2);
    }
    addField3(old) {
        return new TestConceptV3(old.id, old.field1, old.field2, 'default');
    }
}
describe('ReadModelSchemaMigrator', () => {
    const migrations = new Map();
    migrations.set(2, {
        fromSchema: TestConceptV1,
        toSchema: TestConceptV2,
        methodName: 'addField2',
        migrationClass: TestConceptMigration,
        toVersion: 2,
    });
    migrations.set(3, {
        fromSchema: TestConceptV2,
        toSchema: TestConceptV3,
        methodName: 'addField3',
        migrationClass: TestConceptMigration,
        toVersion: 3,
    });
    const config = new framework_types_1.BoosterConfig('test');
    config.schemaMigrations['TestConcept'] = migrations;
    const migrator = new read_model_schema_migrator_1.ReadModelSchemaMigrator(config);
    describe('migrate', async () => {
        it('throws when the schemaVersion of the concept to migrate is lower than 1', async () => {
            const toMigrate = {
                id: 'id',
                boosterMetadata: {
                    version: 0,
                    schemaVersion: 0,
                },
            };
            await (0, expect_1.expect)(migrator.migrate(toMigrate, 'TestConcept')).to.be.rejectedWith(/Received an invalid schema version value, 0, for TestConcept/);
        });
        it('throws when the schemaVersion of the concept to migrate is higher than the current version', async () => {
            const toMigrate = {
                id: 'id',
                boosterMetadata: {
                    version: 0,
                    schemaVersion: 4,
                },
            };
            await (0, expect_1.expect)(migrator.migrate(toMigrate, 'TestConcept')).to.be.rejectedWith(/The current schema version of TestConcept is 3, which is lower than the received version 4/);
        });
        it('does not migrate when the received schemaVersion is the same as the current schemaVersion', async () => {
            const toMigrate = {
                id: 'id',
                boosterMetadata: {
                    version: 0,
                    schemaVersion: 3,
                },
            };
            (0, expect_1.expect)(await migrator.migrate(toMigrate, 'TestConcept')).to.equal(toMigrate);
        });
        it('migrates when the received schemaVersion is lower than the current one', async () => {
            const toMigrate = {
                id: 'id',
                boosterMetadata: {
                    version: 0,
                    schemaVersion: 1,
                },
                field1: 'test-field1',
            };
            const expected = {
                id: 'id',
                boosterMetadata: {
                    version: 0,
                    schemaVersion: 3,
                },
                field1: 'test-field1',
                field2: 2,
                field3: 'default',
            };
            const got = (await migrator.migrate(toMigrate, 'TestConcept'));
            (0, expect_1.expect)(got).not.to.be.equal(toMigrate); // This checks the reference is not the same (i.e. a different object is returned)
            (0, expect_1.expect)(got).to.be.deep.equal(expected);
        });
    });
});
