"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createPassword = void 0;
const faker_1 = require("faker");
const createPassword = () => {
    return `${faker_1.internet.password(8)}Passw0rd!`;
};
exports.createPassword = createPassword;
