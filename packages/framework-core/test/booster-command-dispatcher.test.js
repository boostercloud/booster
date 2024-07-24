"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const booster_1 = require("../src/booster");
const sinon_1 = require("sinon");
const expect_1 = require("./expect");
const booster_command_dispatcher_1 = require("../src/booster-command-dispatcher");
const framework_types_1 = require("@boostercloud/framework-types");
const src_1 = require("../src");
const faker_1 = require("faker");
const booster_authorizer_1 = require("../src/booster-authorizer");
describe('the `BoosterCommandsDispatcher`', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
        booster_1.Booster.configure('test', (config) => {
            config.appName = '';
            for (const propName in config.commandHandlers) {
                delete config.commandHandlers[propName];
            }
        });
    });
    describe('the `dispatchCommand` method', () => {
        it('fails if the command "version" is not sent', () => {
            const command = {
                typeName: 'PostComment',
                value: { comment: 'This comment is pointless' },
            };
            booster_1.Booster.configure('test', async (config) => {
                await (0, expect_1.expect)(new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(command, {})).to.be.eventually.rejectedWith('The required command "version" was not present');
            });
        });
        it('fails if the command is not registered', () => {
            const command = {
                version: 1,
                typeName: 'PostComment',
                value: { comment: 'This comment is pointless' },
            };
            booster_1.Booster.configure('test', async (config) => {
                await (0, expect_1.expect)(new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(command, {})).to.be.eventually.rejectedWith('Could not find a proper handler for PostComment');
            });
        });
        it('fails if the current user is not authorized', async () => {
            class Thor {
            }
            const config = {
                commandHandlers: {
                    UnauthorizedCommand: {
                        authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [Thor]),
                    },
                },
            };
            const commandEnvelope = {
                typeName: 'UnauthorizedCommand',
                version: 'π',
                currentUser: {
                    roles: ['Loki'],
                },
            };
            await (0, expect_1.expect)(new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(commandEnvelope, {})).to.be.eventually.rejectedWith(framework_types_1.NotAuthorizedError);
        });
        it('calls the handler method of a registered command', async () => {
            const fakeHandler = (0, sinon_1.fake)();
            class ProperlyHandledCommand {
                static handle() { }
            }
            (0, sinon_1.replace)(ProperlyHandledCommand, 'handle', fakeHandler);
            (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
            const config = {
                commandHandlers: {
                    ProperlyHandledCommand: {
                        authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
                        before: [],
                        class: ProperlyHandledCommand,
                    },
                },
                currentVersionFor: sinon_1.fake.returns(1),
            };
            const commandValue = {
                something: 'to handle',
            };
            const commandEnvelope = {
                typeName: 'ProperlyHandledCommand',
                version: 'π',
                currentUser: {
                    roles: ['Loki'],
                },
                value: commandValue,
                requestID: '42',
            };
            await new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(commandEnvelope, {});
            (0, expect_1.expect)(fakeHandler).to.have.been.calledWithMatch(commandValue);
        });
        it('allows the handler set the responseHeaders', async () => {
            class ProperlyHandledCommand {
                static handle(command, register) {
                    register.responseHeaders['Test-Header'] = 'test';
                }
            }
            (0, sinon_1.spy)(ProperlyHandledCommand, 'handle');
            (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
            const config = {
                commandHandlers: {
                    ProperlyHandledCommand: {
                        authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
                        before: [],
                        class: ProperlyHandledCommand,
                    },
                },
                currentVersionFor: sinon_1.fake.returns(1),
            };
            const commandValue = {
                something: 'to handle',
            };
            const commandEnvelope = {
                typeName: 'ProperlyHandledCommand',
                version: 'π',
                currentUser: {
                    roles: ['Loki'],
                },
                value: commandValue,
                requestID: '42',
            };
            const context = {
                responseHeaders: {},
            };
            await new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(commandEnvelope, context);
            (0, expect_1.expect)(ProperlyHandledCommand.handle).to.have.been.calledWithMatch(commandValue, { responseHeaders: {} });
            (0, expect_1.expect)(context.responseHeaders).to.deep.equal({ 'Test-Header': 'test' });
        });
        it('properly handles the registered events', async () => {
            class SomethingHappened {
                constructor(when) {
                    this.when = when;
                }
                entityID() {
                    return faker_1.random.uuid();
                }
            }
            const event = new SomethingHappened('right now!');
            const fakeHandler = (0, sinon_1.fake)((_command, register) => {
                register.events(event);
            });
            class ProperlyHandledCommand {
                static handle() { }
            }
            (0, sinon_1.replace)(ProperlyHandledCommand, 'handle', fakeHandler);
            (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
            const config = {
                commandHandlers: {
                    ProperlyHandledCommand: {
                        authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
                        before: [],
                        class: ProperlyHandledCommand,
                    },
                },
                currentVersionFor: sinon_1.fake.returns(1),
            };
            const commandValue = {
                something: 'to handle',
            };
            const commandEnvelope = {
                typeName: 'ProperlyHandledCommand',
                version: 'π',
                currentUser: {
                    roles: ['Loki'],
                },
                value: commandValue,
                requestID: '42',
            };
            await new booster_command_dispatcher_1.BoosterCommandDispatcher(config).dispatchCommand(commandEnvelope, {});
            (0, expect_1.expect)(fakeHandler).to.have.been.calledWithMatch(commandValue);
            (0, expect_1.expect)(src_1.RegisterHandler.handle).to.have.been.calledWithMatch(config, {
                requestID: '42',
                currentUser: commandEnvelope.currentUser,
                eventList: [event],
            });
        });
        it('waits for the handler method of a registered command to finish any async operation', async () => {
            let asyncOperationFinished = false;
            let PostComment = class PostComment {
                constructor(comment) {
                    this.comment = comment;
                }
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
                static async handle(command, _register) {
                    await new Promise((resolve) => setTimeout(resolve, 100));
                    asyncOperationFinished = true;
                }
            };
            PostComment = tslib_1.__decorate([
                (0, src_1.Command)({ authorize: 'all' })
            ], PostComment);
            const command = new PostComment('This test is good!');
            (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
            let boosterConfig;
            booster_1.Booster.configure('test', (config) => {
                boosterConfig = config;
            });
            await new booster_command_dispatcher_1.BoosterCommandDispatcher(boosterConfig).dispatchCommand({
                requestID: '1234',
                version: 1,
                typeName: 'PostComment',
                value: command,
            }, {});
            (0, expect_1.expect)(asyncOperationFinished).to.be.true;
        });
        context('when before hook functions are passed', () => {
            const newComment = 'Look, I changed the message';
            const newCommentV2 = 'Yes, I changed it for a second time';
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const beforeFn = async (input, _currentUser) => {
                input.comment = newComment;
                const result = await Promise.resolve();
                console.log(result);
                return input;
            };
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const beforeFnV2 = async (input, _currentUser) => {
                // To double-check it's really chained
                if (input.comment === newComment)
                    input.comment = newCommentV2;
                const result = await Promise.resolve();
                console.log(result);
                return input;
            };
            it('transforms the input if a before hook function is passed', async () => {
                let transformedInput = {};
                let PostComment = class PostComment {
                    constructor(comment) {
                        this.comment = comment;
                    }
                    static async handle(command) {
                        transformedInput = command;
                    }
                };
                PostComment = tslib_1.__decorate([
                    (0, src_1.Command)({ authorize: 'all', before: [beforeFn] })
                ], PostComment);
                const command = new PostComment('This test is good!');
                (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                let boosterConfig;
                booster_1.Booster.configure('test', (config) => {
                    boosterConfig = config;
                });
                await new booster_command_dispatcher_1.BoosterCommandDispatcher(boosterConfig).dispatchCommand({
                    requestID: '1234',
                    version: 1,
                    typeName: 'PostComment',
                    value: command,
                }, {});
                (0, expect_1.expect)(transformedInput).to.deep.equal(new PostComment(newComment));
            });
            it('transforms the input when more than one before hook function is passed', async () => {
                let transformedInput = {};
                let PostComment = class PostComment {
                    constructor(comment) {
                        this.comment = comment;
                    }
                    static async handle(command) {
                        transformedInput = command;
                    }
                };
                PostComment = tslib_1.__decorate([
                    (0, src_1.Command)({ authorize: 'all', before: [beforeFn, beforeFnV2] })
                ], PostComment);
                const command = new PostComment('This test is good!');
                (0, sinon_1.replace)(src_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                let boosterConfig;
                booster_1.Booster.configure('test', (config) => {
                    boosterConfig = config;
                });
                await new booster_command_dispatcher_1.BoosterCommandDispatcher(boosterConfig).dispatchCommand({
                    requestID: '1234',
                    version: 1,
                    typeName: 'PostComment',
                    value: command,
                }, {});
                (0, expect_1.expect)(transformedInput).to.deep.equal(new PostComment(newCommentV2));
            });
        });
    });
});
