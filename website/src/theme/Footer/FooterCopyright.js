"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FooterCopyright = void 0;
var react_1 = require("react");
var FooterCopyright = function (_a) {
    var _b = _a.copyright, copyright = _b === void 0 ? '' : _b;
    return (<div className="footer-cr">
      <p>
        Booster is an open-source initiative from <a href="https://www.theagilemonkeys.com/">The Agile Monkeys.</a>
      </p>
      <p>
        <a href="https://www.boosterframework.com/privacy-policy">Privacy Policy</a>
      </p>
    </div>);
};
exports.FooterCopyright = FooterCopyright;
exports.default = exports.FooterCopyright;
