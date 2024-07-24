"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var react_1 = require("react");
// A custom link react component was needed because the default onClick function
// of the <a> tag in the .mdx files was not working. This component is used
// in the mdx file to create a link that opens in a new tab and executes
// the custom onClick function.
var CustomLink = function (_a) {
    var href = _a.href, onClick = _a.onClick, children = _a.children;
    var handleClick = function (_event) {
        if (onClick) {
            onClick(); // execute the custom onClick function
        }
    };
    return (<a href={href} target="_blank" rel="noopener noreferrer" onClick={handleClick}>
      {children}
    </a>);
};
exports.default = CustomLink;
