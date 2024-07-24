"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ArtilleryExecutor = void 0;
const fs = require("fs");
const yaml = require("yaml");
const path = require("path");
const aws_sdk_1 = require("aws-sdk");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
const cloudFormation = new aws_sdk_1.CloudFormation();
class ArtilleryExecutor {
    constructor(scriptsFolder, providerTestHelper, stage = 'booster') {
        this.scriptsFolder = scriptsFolder;
        this.providerTestHelper = providerTestHelper;
        this.stage = stage;
        this.serverlessArtilleryStackPrefix = 'serverless-artillery';
    }
    async ensureDeployed() {
        let slsartStack;
        try {
            const { Stacks } = await cloudFormation
                .describeStacks({
                StackName: `${this.serverlessArtilleryStackPrefix}-${this.stage}`,
            })
                .promise();
            if (Stacks === null || Stacks === void 0 ? void 0 : Stacks[0]) {
                slsartStack = Stacks[0];
            }
        }
        catch (e) {
            // The CDK returns an exception when the stack is not found. Ignore it and deploy
        }
        const validStatuses = ['CREATE_COMPLETE', 'UPDATE_COMPLETE'];
        if (slsartStack) {
            if (validStatuses.includes(slsartStack === null || slsartStack === void 0 ? void 0 : slsartStack.StackStatus)) {
                console.info('Serverless Artillery stack is already deployed. Skipping redeployment.');
                return;
            }
            console.info(`Serverless Artillery stack is in a wrong state: ${slsartStack === null || slsartStack === void 0 ? void 0 : slsartStack.StackStatus}. Redeploying.`);
        }
        await (0, framework_common_helpers_1.runCommand)('.', `slsart deploy --stage ${this.stage} -D`);
    }
    async executeScript(scriptName, overrideOptions = {}) {
        const scriptContent = this.getScriptContent(scriptName, overrideOptions);
        await (0, framework_common_helpers_1.runCommand)('.', `slsart invoke --stage ${this.stage} --data '${scriptContent}' -D`);
    }
    getScriptContent(scriptName, options) {
        const scriptPath = path.join(this.scriptsFolder, scriptName);
        const parsedScript = yaml.parse(fs.readFileSync(scriptPath).toString());
        parsedScript.config.target = this.providerTestHelper.outputs.graphqlURL;
        if (options.phases) {
            parsedScript.config.phases = options.phases;
        }
        if (options.variables) {
            for (const [name, value] of Object.entries(options.variables)) {
                parsedScript.config.variables[name] = value;
            }
        }
        return JSON.stringify(parsedScript);
    }
}
exports.ArtilleryExecutor = ArtilleryExecutor;
