import React, { useState } from 'react';
import ThumbsUpIcon from '@site/static/img/thumbs-up.svg'
import ThumbsDownIcon from '@site/static/img/thumbs-down.svg'


export function ThumbsComponent() {
  const [thumbsUpClicked, setThumbsUpClicked] = useState(false);
  const [thumbsDownClicked, setThumbsDownClicked] = useState(false);

  const handleThumbsUpClick = () => {
    setThumbsUpClicked(true);
  };

  const handleThumbsDownClick = () => {
    setThumbsDownClicked(true);
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
