"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const semver_1 = require("../../src/services/semver");
const expect_1 = require("../expect");
describe('Semver class', () => {
    describe('bad construction', () => {
        it('empty version', async () => {
            let exceptionThrown = false;
            try {
                new semver_1.default('');
            }
            catch {
                exceptionThrown = true;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
        it('shorter version', async () => {
            let exceptionThrown = false;
            try {
                new semver_1.default('1.2');
            }
            catch {
                exceptionThrown = true;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
        it('longer version', async () => {
            let exceptionThrown = false;
            try {
                new semver_1.default('1.2.3.4');
            }
            catch {
                exceptionThrown = true;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
    });
    describe('compare versions', () => {
        const versionA = new semver_1.default('1.2.3');
        it('identical versions', async () => {
            const versionB = new semver_1.default('1.2.3');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in fix part greater than', async () => {
            const versionB = new semver_1.default('1.2.4');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in fix part less than', async () => {
            const versionB = new semver_1.default('1.2.2');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in feature part greater than', async () => {
            const versionB = new semver_1.default('1.3.3');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in feature part less than', async () => {
            const versionB = new semver_1.default('1.1.3');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(true);
        });
        it('versions differ in breaking part greater than', async () => {
            const versionB = new semver_1.default('2.2.3');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in breaking part less than', async () => {
            const versionB = new semver_1.default('0.2.3');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in all parts greater than', async () => {
            const versionB = new semver_1.default('4.5.6');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false);
        });
        it('versions differ in all parts less than', async () => {
            const versionB = new semver_1.default('0.1.2');
            (0, expect_1.expect)(versionA.equals(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInBreakingSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFeatureSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.equalsInFixSection(versionB)).to.be.equal(false);
            (0, expect_1.expect)(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(true);
            (0, expect_1.expect)(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(true);
        });
    });
});
