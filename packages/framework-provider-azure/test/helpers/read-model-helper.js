"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createMockReadModel = void 0;
const faker_1 = require("faker");
function createMockReadModel() {
    return {
        id: faker_1.random.uuid(),
        some: 'object',
    };
}
exports.createMockReadModel = createMockReadModel;
