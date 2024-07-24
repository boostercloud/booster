"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FooterLinkSection = void 0;
var react_1 = require("react");
var FooterLinkSection = function (_a) {
    var _b = _a.links, links = _b === void 0 ? [] : _b;
    return (<div className="footer-ls">
      {links.map(function (_a) {
            var title = _a.title, items = _a.items;
            return (<div key={title} className="footer-ls-section">
          <h5 className="footer-ls-section-title">{title}</h5>
          <div className="footer-ls-column">
            {items.map(function (_a, i) {
                    var to = _a.to, label = _a.label;
                    return (<a key={i} href={to}>
                {label}
              </a>);
                })}
          </div>
        </div>);
        })}
    </div>);
};
exports.FooterLinkSection = FooterLinkSection;
exports.default = exports.FooterLinkSection;
