"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var react_1 = require("react");
// import { useColorMode } from "@docusaurus/theme-common";
var useUtterance_1 = require("../hooks/useUtterance");
function Utterance(props) {
    // const { colorMode } = useColorMode();
    var colorMode = 'light';
    var options = (0, react_1.useMemo)(function () {
        return ({
            repo: 'boostercloud/docs-discussion',
            theme: "github-".concat(colorMode),
            label: 'comment-section',
        });
    }, [colorMode]);
    var anchor = (0, useUtterance_1.default)(options).anchor;
    return <div ref={anchor} {...props}/>;
}
exports.default = Utterance;
