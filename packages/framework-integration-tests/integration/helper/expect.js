"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.expect = void 0;
const chai = require("chai");
const sinonChai = require("sinon-chai");
const chaiAsPromised = require("chai-as-promised");
const chaiArrays = require("chai-arrays");
chai.use(sinonChai);
chai.use(chaiAsPromised);
chai.use(chaiArrays);
exports.expect = chai.expect;
