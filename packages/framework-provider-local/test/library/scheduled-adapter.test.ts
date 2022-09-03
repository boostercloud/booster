import { BoosterConfig } from "@boostercloud/framework-types"
import { expect } from '../expect'
import { fake, SinonStub, stub } from "sinon"
import { rawScheduledInputToEnvelope } from "../../src/library/scheduled-adapter"
import { describe } from 'mocha'

describe('scheduled-adapter', () => {
    let mockConfig: BoosterConfig
    let loggerDebugStub: SinonStub

    beforeEach(() => {
        mockConfig = new BoosterConfig('test')
        mockConfig.appName = 'nuke-button'

        loggerDebugStub = stub()

        mockConfig.logger = {
            info: fake(),
            warn: fake(),
            error: fake(),
            debug: loggerDebugStub,
        }
    })

    describe('rawScheduledInputToEnvelope', () => {
        [{typeName: ""}, {typeName: undefined}].forEach(function (arg) {
            it('should throw an error when typeName is empty or undefined', async () => {
                await expect(rawScheduledInputToEnvelope(mockConfig, arg)).to.eventually.be.rejectedWith(
                    new Error(
                        `typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave ${JSON.stringify(
                            arg
                        )}`
                    ).message
                )
            })
        })

        it('should return a ScheduledCommandEnvelope with the same typeName', async () => {
            const input = {typeName: "dummy"}
            const results = await rawScheduledInputToEnvelope(mockConfig, input)
            expect(results.typeName).to.deep.equal(input.typeName)
        })
    })
})