"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.expect = void 0;
const chai = require("chai");
const sinonChai = require("sinon-chai");
const chaiAsPromised = require("chai-as-promised");
chai.use(sinonChai);
chai.use(chaiAsPromised);
exports.expect = chai.expect;
