"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const fc = require("fast-check");
const expect_1 = require("./expect");
const src_1 = require("../src");
describe('the config type', () => {
    describe('resourceNames', () => {
        it('fails to get if the app name is empty', () => {
            const cfg = new src_1.BoosterConfig('test');
            cfg.appName = '';
            (0, expect_1.expect)(() => cfg.resourceNames).to.throw();
        });
        it('gets the application stack name from the app name', () => {
            fc.assert(fc.property(fc.string({ minLength: 1, maxLength: 10 }), (appName) => {
                const cfg = new src_1.BoosterConfig('test');
                cfg.appName = appName;
                (0, expect_1.expect)(cfg.resourceNames.applicationStack).to.equal(`${appName}-app`);
            }));
        });
        it('gets the events store name from the app name', () => {
            fc.assert(fc.property(fc.string({ minLength: 1, maxLength: 10 }), (appName) => {
                const cfg = new src_1.BoosterConfig('test');
                cfg.appName = appName;
                (0, expect_1.expect)(cfg.resourceNames.eventsStore).to.equal(`${appName}-app-events-store`);
            }));
        });
        it('gets well-formatted readmodel names, based on the application name', () => {
            fc.assert(fc.property(fc.string({ minLength: 1, maxLength: 10 }), fc.string({ minLength: 1, maxLength: 10 }), (appName, readModelName) => {
                const cfg = new src_1.BoosterConfig('test');
                cfg.appName = appName;
                (0, expect_1.expect)(cfg.resourceNames.forReadModel(readModelName)).to.equal(`${appName}-app-${readModelName}`);
            }));
        });
    });
    describe('thereAreRoles', () => {
        it('returns true when there are roles defined', () => {
            const config = new src_1.BoosterConfig('test');
            config.roles['test-role'] = {
                auth: {
                    signUpMethods: [],
                },
            };
            (0, expect_1.expect)(config.thereAreRoles).to.be.equal(true);
        });
        it('returns false when there are no roles defined', () => {
            const config = new src_1.BoosterConfig('test');
            (0, expect_1.expect)(config.thereAreRoles).to.be.equal(false);
        });
    });
    describe('currentVersionFor', () => {
        it('returns 1 when the concept does not have any migration defined', () => {
            const config = new src_1.BoosterConfig('test');
            const schemaMigrations = new Map();
            schemaMigrations.set(2, {});
            config.schemaMigrations['concept-with-migrations'] = schemaMigrations;
            (0, expect_1.expect)(config.currentVersionFor('concept-without-migration')).to.be.equal(1);
        });
        it('returns the version of the latest schema migration', () => {
            class SchemaTest {
            }
            class SchemaMigrationClassTest {
            }
            const config = new src_1.BoosterConfig('test');
            const schemaMigrations = new Map();
            schemaMigrations.set(3, {
                fromSchema: SchemaTest,
                toSchema: SchemaTest,
                methodName: 'method3',
                migrationClass: SchemaMigrationClassTest,
                toVersion: 3,
            });
            schemaMigrations.set(2, {
                fromSchema: SchemaTest,
                toSchema: SchemaTest,
                methodName: 'method2',
                migrationClass: SchemaMigrationClassTest,
                toVersion: 2,
            });
            config.schemaMigrations['concept'] = schemaMigrations;
            (0, expect_1.expect)(config.currentVersionFor('concept')).to.be.equal(3);
        });
    });
    describe('validate', () => {
        it('throws when there are gaps in the migration versions for a concept', () => {
            const config = new src_1.BoosterConfig('test');
            config.provider = {};
            const schemaMigrations = new Map();
            schemaMigrations.set(3, {});
            schemaMigrations.set(2, {});
            schemaMigrations.set(5, {});
            config.schemaMigrations['concept'] = schemaMigrations;
            (0, expect_1.expect)(() => config.validate()).to.throw(/Schema Migrations for 'concept' are invalid/);
        });
        it('does not throw when there are no gaps in the migration versions for a concept', () => {
            const config = new src_1.BoosterConfig('test');
            config.provider = {};
            const schemaMigrations = new Map();
            schemaMigrations.set(4, {});
            schemaMigrations.set(2, {});
            schemaMigrations.set(3, {});
            config.schemaMigrations['concept'] = schemaMigrations;
            (0, expect_1.expect)(() => config.validate()).to.not.throw();
        });
    });
    describe('provider', () => {
        it('throws when there is no provider set', () => {
            const config = new src_1.BoosterConfig('test');
            (0, expect_1.expect)(() => config.provider).to.throw(/set a valid provider runtime/);
        });
        it('does not throw when there is a provider set', () => {
            const config = new src_1.BoosterConfig('test');
            config.provider = {};
            (0, expect_1.expect)(() => config.provider).to.not.throw();
        });
    });
});
