"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var react_1 = require("react");
var styles_module_css_1 = require("./styles.module.css");
function TerminalWindow(_a) {
    var children = _a.children;
    return (<div className={styles_module_css_1.default.terminalWindow}>
      <div className={styles_module_css_1.default.terminalWindowHeader}>
        <div className={styles_module_css_1.default.buttons}>
          <span className={styles_module_css_1.default.dot} style={{ background: '#f25f58' }}/>
          <span className={styles_module_css_1.default.dot} style={{ background: '#fbbe3c' }}/>
          <span className={styles_module_css_1.default.dot} style={{ background: '#58cb42' }}/>
        </div>
      </div>

      <div className={styles_module_css_1.default.terminalWindowBody}>{children}</div>
    </div>);
}
exports.default = TerminalWindow;
