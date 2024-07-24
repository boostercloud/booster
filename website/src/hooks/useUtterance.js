"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
Object.defineProperty(exports, "__esModule", { value: true });
var react_1 = require("react");
function useUtterance(params) {
    var anchor = (0, react_1.useRef)(null);
    (0, react_1.useEffect)(function () {
        while (anchor.current.firstChild) {
            anchor.current.removeChild(anchor.current.firstChild);
        }
        anchor.current.appendChild(createUtteranceScript(params));
    }, [params]);
    return { anchor: anchor };
}
exports.default = useUtterance;
function createUtteranceScript(option) {
    var script = document.createElement('script');
    script.src = 'https://utteranc.es/client.js';
    script.crossOrigin = 'anonymous';
    script.async = true;
    Object.entries(__assign(__assign({}, defaultAttributes), option)).forEach(function (_a) {
        var key = _a[0], value = _a[1];
        script.setAttribute(key, value);
    });
    return script;
}
var defaultAttributes = {
    'issue-term': 'title',
    label: 'comment-section',
    theme: 'github-light',
};
