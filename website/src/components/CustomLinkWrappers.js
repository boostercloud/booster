"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CLInstallBooster = exports.CLAskMeRepo = exports.CLExampleApps = exports.CLStepByStep = void 0;
var react_1 = require("react");
var analytics_client_1 = require("./Analytics/analytics-client");
var CustomLink_1 = require("./CustomLink");
var CLStepByStep = function (_a) {
    var children = _a.children;
    return createCustomLinkComponent(children, 'GYHPPIBS');
};
exports.CLStepByStep = CLStepByStep;
var CLExampleApps = function (_a) {
    var children = _a.children;
    return createCustomLinkComponent(children, 'YY7T3ZSZ');
};
exports.CLExampleApps = CLExampleApps;
var CLAskMeRepo = function (_a) {
    var children = _a.children;
    return createCustomLinkComponent(children, 'NE1EADCK');
};
exports.CLAskMeRepo = CLAskMeRepo;
var CLInstallBooster = function (_a) {
    var children = _a.children;
    return createCustomLinkComponent(children, 'AXTW7ICE');
};
exports.CLInstallBooster = CLInstallBooster;
function createCustomLinkComponent(element, event) {
    var _a = extractLinkInfo(element), text = _a.text, href = _a.href;
    var onClick = function () { return analytics_client_1.AnalyticsClient.startAndTrackEvent(event); };
    return <CustomLink_1.default href={href} onClick={onClick}>{text}</CustomLink_1.default>;
}
function extractLinkInfo(element) {
    if (react_1.default.isValidElement(element) && element.props.href) {
        return { text: element.props.children, href: element.props.href };
    }
    return { text: '', href: '' };
}
