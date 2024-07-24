"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ThumbsComponent = void 0;
var react_1 = require("react");
var thumbs_up_svg_1 = require("@site/static/img/thumbs-up.svg");
var thumbs_down_svg_1 = require("@site/static/img/thumbs-down.svg");
var chat_service_1 = require("@site/src/theme/chat-service");
var ThumbsComponent = function (_a) {
    var questionId = _a.questionId;
    var _b = (0, react_1.useState)(false), thumbsUpClicked = _b[0], setThumbsUpClicked = _b[1];
    var _c = (0, react_1.useState)(false), thumbsDownClicked = _c[0], setThumbsDownClicked = _c[1];
    var handleThumbsUpClick = function () {
        setThumbsUpClicked(true);
        chat_service_1.ChatService.reactToAnswer(questionId, chat_service_1.AnswerReaction.Upvoted);
    };
    var handleThumbsDownClick = function () {
        setThumbsDownClicked(true);
        chat_service_1.ChatService.reactToAnswer(questionId, chat_service_1.AnswerReaction.Downvoted);
    };
    return (<div className='bc-thumbs-container'>
      {thumbsDownClicked ? null : (<button disabled={thumbsUpClicked || thumbsDownClicked} className='bc-thumbs-button' onClick={handleThumbsUpClick}>
          <thumbs_up_svg_1.default className='bc-thumbs-icon'/>
        </button>)}
      {thumbsUpClicked ? null : (<button disabled={thumbsUpClicked || thumbsDownClicked} className='bc-thumbs-button' onClick={handleThumbsDownClick}>
          <thumbs_down_svg_1.default className='bc-thumbs-icon'/>
        </button>)}
    </div>);
};
exports.ThumbsComponent = ThumbsComponent;
