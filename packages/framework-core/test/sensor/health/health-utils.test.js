"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
require("mocha");
const health_utils_1 = require("../../../src/sensor/health/health-utils");
const expect_1 = require("../../expect");
const sensor_1 = require("../../../src/sensor");
describe('Health utils', () => {
    let root;
    let rootChildren1;
    let rootChildren2;
    let rootChildren1Children1;
    let rootChildren1Children2;
    let healthProviders;
    beforeEach(() => {
        root = {
            class: sensor_1.BoosterHealthIndicator,
            healthIndicatorConfiguration: {
                id: 'root',
                name: 'root',
                enabled: true,
                details: true,
                showChildren: true,
            },
        };
        rootChildren1 = {
            class: sensor_1.BoosterHealthIndicator,
            healthIndicatorConfiguration: {
                id: 'root/rootChildren1',
                name: 'root/rootChildren1',
                enabled: true,
                details: true,
                showChildren: true,
            },
        };
        rootChildren2 = {
            class: sensor_1.BoosterHealthIndicator,
            healthIndicatorConfiguration: {
                id: 'root/rootChildren2',
                name: 'root/rootChildren2',
                enabled: true,
                details: true,
                showChildren: true,
            },
        };
        rootChildren1Children1 = {
            class: sensor_1.BoosterHealthIndicator,
            healthIndicatorConfiguration: {
                id: 'root/rootChildren1/rootChildren1Children1',
                name: 'root/rootChildren1/rootChildren1Children1',
                enabled: true,
                details: true,
                showChildren: true,
            },
        };
        rootChildren1Children2 = {
            class: sensor_1.BoosterHealthIndicator,
            healthIndicatorConfiguration: {
                id: 'root/rootChildren1/rootChildren1Children2',
                name: 'root/rootChildren1/rootChildren1Children2',
                enabled: true,
                details: true,
                showChildren: true,
            },
        };
        healthProviders = {
            root: root,
            [rootChildren1.healthIndicatorConfiguration.id]: rootChildren1,
            [rootChildren2.healthIndicatorConfiguration.id]: rootChildren2,
            [rootChildren1Children1.healthIndicatorConfiguration.id]: rootChildren1Children1,
            [rootChildren1Children2.healthIndicatorConfiguration.id]: rootChildren1Children2,
        };
    });
    it('isEnabled return true if all are true', () => {
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren2, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children2, healthProviders)).to.be.true;
    });
    it('isEnabled return false in a component but not in parents or siblings', () => {
        healthProviders[rootChildren1Children1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.enabled = false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children2, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren2, healthProviders)).to.be.true;
    });
    it('isEnabled return false in a component and all the children but not siblings', () => {
        healthProviders[rootChildren1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.enabled = false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren1Children2, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.isEnabled)(rootChildren2, healthProviders)).to.be.true;
    });
    it('showChildren return true if all are true', () => {
        (0, expect_1.expect)((0, health_utils_1.showChildren)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren2, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children2, healthProviders)).to.be.true;
    });
    it('showChildren return false in a component but not in parents or siblings', () => {
        healthProviders[rootChildren1Children1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.showChildren =
            false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children2, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren2, healthProviders)).to.be.true;
    });
    it('showChildren return false in a component and all the children but not siblings', () => {
        healthProviders[rootChildren1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.showChildren = false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(root, healthProviders)).to.be.true;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children1, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren1Children2, healthProviders)).to.be.false;
        (0, expect_1.expect)((0, health_utils_1.showChildren)(rootChildren2, healthProviders)).to.be.true;
    });
    it('metadataFromId', () => {
        (0, expect_1.expect)(() => (0, health_utils_1.metadataFromId)(healthProviders, '')).to.throw('Unexpected HealthProvider id ');
        (0, expect_1.expect)(() => (0, health_utils_1.metadataFromId)(healthProviders, 'xxx')).to.throw('Unexpected HealthProvider id ');
        (0, expect_1.expect)((0, health_utils_1.metadataFromId)(healthProviders, 'root')).to.be.deep.equal(root);
        (0, expect_1.expect)((0, health_utils_1.metadataFromId)(healthProviders, 'root/rootChildren1')).to.be.deep.equal(rootChildren1);
        (0, expect_1.expect)((0, health_utils_1.metadataFromId)(healthProviders, 'root/rootChildren2')).to.be.deep.equal(rootChildren2);
        (0, expect_1.expect)((0, health_utils_1.metadataFromId)(healthProviders, 'root/rootChildren1/rootChildren1Children1')).to.be.deep.equal(rootChildren1Children1);
        (0, expect_1.expect)((0, health_utils_1.metadataFromId)(healthProviders, 'root/rootChildren1/rootChildren1Children2')).to.be.deep.equal(rootChildren1Children2);
    });
    it('parentId', () => {
        (0, expect_1.expect)((0, health_utils_1.parentId)(root)).to.be.eq('');
        (0, expect_1.expect)((0, health_utils_1.parentId)(rootChildren1)).to.be.eq('root');
        (0, expect_1.expect)((0, health_utils_1.parentId)(rootChildren2)).to.be.eq('root');
        (0, expect_1.expect)((0, health_utils_1.parentId)(rootChildren1Children1)).to.be.eq('root/rootChildren1');
        (0, expect_1.expect)((0, health_utils_1.parentId)(rootChildren1Children2)).to.be.eq('root/rootChildren1');
    });
    it('rootHealthProviders', () => {
        (0, expect_1.expect)((0, health_utils_1.rootHealthProviders)(healthProviders)).to.be.deep.equal([root]);
    });
    it('childrenHealthProviders', () => {
        (0, expect_1.expect)((0, health_utils_1.childrenHealthProviders)(root, healthProviders)).to.be.deep.equal([rootChildren1, rootChildren2]);
        (0, expect_1.expect)((0, health_utils_1.childrenHealthProviders)(rootChildren1, healthProviders)).to.be.deep.equal([
            rootChildren1Children1,
            rootChildren1Children2,
        ]);
        (0, expect_1.expect)((0, health_utils_1.childrenHealthProviders)(rootChildren1Children1, healthProviders)).to.be.deep.equal([]);
        (0, expect_1.expect)((0, health_utils_1.childrenHealthProviders)(rootChildren1Children2, healthProviders)).to.be.deep.equal([]);
        (0, expect_1.expect)((0, health_utils_1.childrenHealthProviders)(rootChildren2, healthProviders)).to.be.deep.equal([]);
    });
});
