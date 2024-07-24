"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var theme_common_1 = require("@docusaurus/theme-common");
var react_1 = require("react");
var FooterCopyright_1 = require("./FooterCopyright");
var FooterLinkSection_1 = require("./FooterLinkSection");
function Footer() {
    var footer = (0, theme_common_1.useThemeConfig)().footer;
    if (!footer) {
        return null;
    }
    var copyright = footer.copyright, links = footer.links;
    return (<>
      <footer className="footer-container">
        <FooterLinkSection_1.default links={links}/>
        <FooterCopyright_1.default copyright={copyright}/>
      </footer>
    </>);
}
exports.default = react_1.default.memo(Footer);
