"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../expect");
const utils_1 = require("../../../src/infrastructure/helper/utils");
const utils_2 = require("../../../dist/infrastructure/helper/utils");
describe('Users want to use utility methods', () => {
    describe('and build the terraform name', () => {
        it('with a simple name', () => {
            const result = (0, utils_1.toTerraformName)('name', '');
            (0, expect_1.expect)(result).to.be.equal('name');
        });
        it('with a simple name and suffix', () => {
            const result = (0, utils_1.toTerraformName)('name', 'suffix');
            (0, expect_1.expect)(result).to.be.equal('namesuffix');
        });
        it('with a long name and suffix return last name characters and the whole suffix', () => {
            const result = (0, utils_1.toTerraformName)('0123456789012345678901234', 'suffix');
            (0, expect_1.expect)(result).to.be.equal('B89012345678901234suffix');
        });
        it('with a name and a 24 characters suffix return name and first suffix characters', () => {
            const result = (0, utils_1.toTerraformName)('name', '012345678901234567890123');
            (0, expect_1.expect)(result).to.be.equal('name0123456789012345678');
        });
    });
    describe('and clean an Azure name', () => {
        it('with a simple name', () => {
            const result = (0, utils_1.toAzureName)('name');
            (0, expect_1.expect)(result).to.be.equal('name');
        });
        it('with a name longer than expected', () => {
            const result = (0, utils_1.toAzureName)('0123456789012345678901234567890');
            (0, expect_1.expect)(result).to.be.equal('012345678901234567890123');
        });
        it('with invalid characters in the name', () => {
            const result = (0, utils_1.toAzureName)('0-_-1_-_2');
            (0, expect_1.expect)(result).to.be.equal('012');
        });
    });
    describe('and build the app prefix', () => {
        it('with a simple name', () => {
            const config = { appName: 'appName', environmentName: 'environmentName' };
            const result = (0, utils_1.buildAppPrefix)(config);
            (0, expect_1.expect)(result).to.be.equal('appnameenvironmentname');
        });
        it('with a name longer than expected', () => {
            const config = { appName: 'appName0123456789', environmentName: 'environmentName0123456789' };
            const result = (0, utils_1.buildAppPrefix)(config);
            (0, expect_1.expect)(result).to.be.equal('appname0123456789environ');
        });
    });
    describe('and create a resource group name', () => {
        it('with a simple name', () => {
            const result = (0, utils_2.createResourceGroupName)('appName', 'environmentName');
            (0, expect_1.expect)(result).to.be.equal('appNameenvironmentNarg');
        });
        it('with a name longer than expected', () => {
            const result = (0, utils_2.createResourceGroupName)('appName0123456789', 'environmentName0123456789');
            (0, expect_1.expect)(result).to.be.equal('appName0123456789envrg');
        });
    });
});
