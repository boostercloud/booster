"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators");
const src_1 = require("../../src");
// Entities to test the annotations
class Product {
}
class ProductV1 {
}
class ProductV2 {
    constructor(field2, field3) {
        this.field2 = field2;
        this.field3 = field3;
    }
}
class ProductV3 {
    constructor(field2, field3, field4) {
        this.field2 = field2;
        this.field3 = field3;
        this.field4 = field4;
    }
}
class ProductV4 {
    constructor(field2, field3, field4, field5) {
        this.field2 = field2;
        this.field3 = field3;
        this.field4 = field4;
        this.field5 = field5;
    }
}
class ProductV5 extends Product {
} // This would be the current version
describe('the `ToVersion` decorator', () => {
    it('throws when a version smaller than 1 is specified', () => {
        (0, expect_1.expect)(() => {
            // @ts-ignore: Unused class
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            class MigrateProduct {
                async changeField2(x) {
                    return {};
                }
            }
            tslib_1.__decorate([
                (0, decorators_1.ToVersion)(1, { fromSchema: ProductV1, toSchema: ProductV2 })
            ], MigrateProduct.prototype, "changeField2", null);
        }).to.throw('Migration versions must always be greater than 1');
    });
    it('adds migrations as metadata to the migration class', () => {
        class MigrateProduct {
            async changeField2(x) {
                return {};
            }
            async changeField3(x) {
                return {};
            }
        }
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(2, { fromSchema: ProductV1, toSchema: ProductV2 })
        ], MigrateProduct.prototype, "changeField2", null);
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(3, { fromSchema: ProductV2, toSchema: ProductV3 })
        ], MigrateProduct.prototype, "changeField3", null);
        const expectedMetadata = [
            {
                migrationClass: MigrateProduct,
                methodName: 'changeField2',
                toVersion: 2,
                fromSchema: ProductV1,
                toSchema: ProductV2,
            },
            {
                migrationClass: MigrateProduct,
                methodName: 'changeField3',
                toVersion: 3,
                fromSchema: ProductV2,
                toSchema: ProductV3,
            },
        ];
        const gotMetadata = Reflect.getMetadata('booster:migrationsMethods', MigrateProduct);
        (0, expect_1.expect)(gotMetadata).to.be.deep.equal(expectedMetadata);
    });
});
describe('the `Migrates` annotation', () => {
    afterEach(() => {
        src_1.Booster.configure('test', (config) => {
            config.appName = '';
            for (const propName in config.schemaMigrations) {
                delete config.schemaMigrations[propName];
            }
        });
    });
    it('adds several migrations correctly', () => {
        let MigrateProductFrom1To3 = class MigrateProductFrom1To3 {
            async changeField2(x) {
                return {};
            }
            async changeField3(x) {
                return {};
            }
        };
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(2, { fromSchema: ProductV1, toSchema: ProductV2 })
        ], MigrateProductFrom1To3.prototype, "changeField2", null);
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(3, { fromSchema: ProductV2, toSchema: ProductV3 })
        ], MigrateProductFrom1To3.prototype, "changeField3", null);
        MigrateProductFrom1To3 = tslib_1.__decorate([
            (0, decorators_1.SchemaMigration)(Product)
        ], MigrateProductFrom1To3);
        let MigrateProductFrom3To5 = class MigrateProductFrom3To5 {
            async changeField5(x) {
                return {};
            }
            async changeField4(x) {
                return {};
            }
        };
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(5, { fromSchema: ProductV4, toSchema: ProductV5 })
        ], MigrateProductFrom3To5.prototype, "changeField5", null);
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(4, { fromSchema: ProductV3, toSchema: ProductV4 })
        ], MigrateProductFrom3To5.prototype, "changeField4", null);
        MigrateProductFrom3To5 = tslib_1.__decorate([
            (0, decorators_1.SchemaMigration)(Product)
        ], MigrateProductFrom3To5);
        src_1.Booster.configure('test', (config) => {
            (0, expect_1.expect)(Object.keys(config.schemaMigrations).length).to.be.equal(1);
            const productMigrations = config.schemaMigrations[Product.name];
            (0, expect_1.expect)(productMigrations.size).to.be.equal(4);
            (0, expect_1.expect)(productMigrations.get(2)).to.be.deep.equal({
                migrationClass: MigrateProductFrom1To3,
                methodName: 'changeField2',
                toVersion: 2,
                fromSchema: ProductV1,
                toSchema: ProductV2,
            });
            (0, expect_1.expect)(productMigrations.get(3)).to.be.deep.equal({
                migrationClass: MigrateProductFrom1To3,
                methodName: 'changeField3',
                toVersion: 3,
                fromSchema: ProductV2,
                toSchema: ProductV3,
            });
            (0, expect_1.expect)(productMigrations.get(4)).to.be.deep.equal({
                migrationClass: MigrateProductFrom3To5,
                methodName: 'changeField4',
                toVersion: 4,
                fromSchema: ProductV3,
                toSchema: ProductV4,
            });
            (0, expect_1.expect)(productMigrations.get(5)).to.be.deep.equal({
                migrationClass: MigrateProductFrom3To5,
                methodName: 'changeField5',
                toVersion: 5,
                fromSchema: ProductV4,
                toSchema: ProductV5,
            });
        });
    });
    it('throws when a migration is duplicated', () => {
        let MigrateProductFrom1To3 = 
        // @ts-ignore: Unused class
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        class MigrateProductFrom1To3 {
            async changeField2(x) {
                return {};
            }
            async changeField3(x) {
                return {};
            }
        };
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(2, { fromSchema: ProductV1, toSchema: ProductV2 })
        ], MigrateProductFrom1To3.prototype, "changeField2", null);
        tslib_1.__decorate([
            (0, decorators_1.ToVersion)(3, { fromSchema: ProductV2, toSchema: ProductV3 })
        ], MigrateProductFrom1To3.prototype, "changeField3", null);
        MigrateProductFrom1To3 = tslib_1.__decorate([
            (0, decorators_1.SchemaMigration)(Product)
            // @ts-ignore: Unused class
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
        ], MigrateProductFrom1To3);
        (0, expect_1.expect)(() => {
            let MigrateProductFrom3To5 = 
            // @ts-ignore: Unused class
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            class MigrateProductFrom3To5 {
                async changeField5(x) {
                    return {};
                }
            };
            tslib_1.__decorate([
                (0, decorators_1.ToVersion)(3, { fromSchema: ProductV2, toSchema: ProductV3 })
            ], MigrateProductFrom3To5.prototype, "changeField5", null);
            MigrateProductFrom3To5 = tslib_1.__decorate([
                (0, decorators_1.SchemaMigration)(Product)
                // @ts-ignore: Unused class
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
            ], MigrateProductFrom3To5);
        }).to.throw(/There is an already defined migration for version 3/);
    });
    it('throws when no migration methods are found', () => {
        (0, expect_1.expect)(() => {
            let MigrateProductFrom3To5 = 
            // @ts-ignore: Unused class
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            class MigrateProductFrom3To5 {
                async changeField5(x) {
                    return {};
                }
            };
            MigrateProductFrom3To5 = tslib_1.__decorate([
                (0, decorators_1.SchemaMigration)(Product)
                // @ts-ignore: Unused class
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
            ], MigrateProductFrom3To5);
        }).to.throw(/No migration methods found in this class/);
    });
});
