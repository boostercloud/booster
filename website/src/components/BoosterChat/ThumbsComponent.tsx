import React, { FC, useState } from 'react'
import ThumbsUpIcon from '@site/static/img/thumbs-up.svg'
import ThumbsDownIcon from '@site/static/img/thumbs-down.svg'
import { AnswerReaction, ChatService } from '@site/src/theme/chat-service'

interface ThumbsComponentProps {
    questionId: string
  }

export const ThumbsComponent: FC<ThumbsComponentProps> = ({ questionId }) => {
  const [thumbsUpClicked, setThumbsUpClicked] = useState(false);
  const [thumbsDownClicked, setThumbsDownClicked] = useState(false);

  const handleThumbsUpClick = () => {
    setThumbsUpClicked(true);
    ChatService.reactToAnswer(questionId, AnswerReaction.Upvoted)
  };

  const handleThumbsDownClick = () => {
    setThumbsDownClicked(true);
    ChatService.reactToAnswer(questionId, AnswerReaction.Downvoted)
  };

  return (
    <div className='bc-thumbs-container'>
      {thumbsDownClicked ? null : (
        <button
          disabled={thumbsUpClicked || thumbsDownClicked}
          className='bc-thumbs-button'
          onClick={handleThumbsUpClick}
        >
          <ThumbsUpIcon className='bc-thumbs-icon' />
        </button>
      )}
      {thumbsUpClicked ? null : (
        <button
          disabled={thumbsUpClicked || thumbsDownClicked}
          className='bc-thumbs-button'
          onClick={handleThumbsDownClick}
        >
          <ThumbsDownIcon className='bc-thumbs-icon' />
        </button>
      )}
    </div>
  );
}
