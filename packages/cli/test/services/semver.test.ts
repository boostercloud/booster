import Semver from '../../src/services/semver'
import { expect } from '../expect'

describe('Semver class', () => {

    describe('bad construction', () => {
        it('empty version', async () => {
            let exceptionThrown = false
            try {
                new Semver('')
            } catch {
                exceptionThrown = true
            }            
            expect(exceptionThrown).to.be.equal(true)
        })

        it('shorter version', async () => {
            let exceptionThrown = false
            try {
                new Semver('1.2')
            } catch {
                exceptionThrown = true
            }            
            expect(exceptionThrown).to.be.equal(true)
        })

        it('longer version', async () => {
            let exceptionThrown = false
            try {
                new Semver('1.2.3.4')
            } catch {
                exceptionThrown = true
            }            
            expect(exceptionThrown).to.be.equal(true)
        })
    })

    describe('compare versions', () => {
        const versionA = new Semver('1.2.3')

        it('identical versions', async () => {
            const versionB = new Semver('1.2.3')
            expect(versionA.equals(versionB)).to.be.equal(true)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(true)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in fix part greater than', async () => {
            const versionB = new Semver('1.2.4')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(false)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in fix part less than', async () => {
            const versionB = new Semver('1.2.2')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(false)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in feature part greater than', async () => {
            const versionB = new Semver('1.3.3')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(true)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in feature part less than', async () => {
            const versionB = new Semver('1.1.3')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(true)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(true)
        })

        it('versions differ in breaking part greater than', async () => {
            const versionB = new Semver('2.2.3')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(true)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in breaking part less than', async () => {
            const versionB = new Semver('0.2.3')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(true)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(true)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(true)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in all parts greater than', async () => {
            const versionB = new Semver('4.5.6')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(false)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(false)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(false)
        })

        it('versions differ in all parts less than', async () => {
            const versionB = new Semver('0.1.2')
            expect(versionA.equals(versionB)).to.be.equal(false)
            expect(versionA.equalsInBreakingSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFeatureSection(versionB)).to.be.equal(false)
            expect(versionA.equalsInFixSection(versionB)).to.be.equal(false)
            expect(versionA.greaterInBreakingSectionThan(versionB)).to.be.equal(true)
            expect(versionA.greaterInFeatureSectionThan(versionB)).to.be.equal(true)
        })
    })
})
