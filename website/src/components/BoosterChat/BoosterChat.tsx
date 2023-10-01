const { ChatService } = require(`${__dirname}/../../theme/chat-service`)
import PrivateGPTSearchIcon from '@site/static/img/privateGptIcon.svg'
import React, { useEffect, useRef, useState } from 'react'
import { AnalyticsClient } from '../Analytics/analytics-client'
import { ChatResponse } from './ChatResponse'

export const ASK_AI_ERROR = 'There was an unexpected error. Please try again 🙏'

export default function BoosterChat(): JSX.Element {
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [hasFinished, setHasFinished] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)
  const [questionId, setQuestionId] = useState<string | null>(null)

  useEffect(() => {
    AnalyticsClient.start()
  }, [])

  const handleResponseUpdated = (_questionId, _question, newResponseFragment, _hasFinished) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
    setHasFinished(_hasFinished)
    setQuestionId(_questionId)
  }

  const handleSearch = async (query: string) => {
    if (query?.trim() === '') {
      return
    }

    AnalyticsClient.trackEvent('CEJF3EH1')
    setHasSearched(true)
    setLoading(true)
    setResponse('')
    setHasFinished(false)

    ChatService.answerBoosterQuestion(query, handleResponseUpdated)
      .catch((error) => {
        setResponse(ASK_AI_ERROR)
        AnalyticsClient.trackEvent('SFWQOOY0')
        console.error(error)
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const handleKeyDown = (event) => {
    if (!loading && event.key === 'Enter') {
      handleSearch(event.target.value)
    }
  }

  const onQuickQuestionClick = async (query: string) => {
    // Show the query in the search bar:
    const searchInput = document.getElementsByClassName('bc-input')[0] as HTMLInputElement
    if (searchInput) {
      searchInput.value = query
    }

    handleSearch(query)
  }

  const resetSearchResponse = () => {
    setResponse(null)
    setLoading(null)
    setHasFinished(false)
  }

  return (
    <div className="bc-layout">
      <AskAIBar
        handleKeyDown={handleKeyDown}
        loading={loading}
        isModalStyle={false}
        hasFinished={hasFinished}
        resetSearchResponse={resetSearchResponse}
      />
      <AskAIDisclaimer />
      {!hasSearched && (
        <div className="bc-quick-questions-panel">
          <button className="bc-quick-question" onClick={() => onQuickQuestionClick('What is Booster Framework?')}>
            What is Booster Framework?
          </button>
          <button className="bc-quick-question" onClick={() => onQuickQuestionClick("Summary of Booster's components")}>
            Summary of Booster's components
          </button>
          <button
            className="bc-quick-question"
            onClick={() => onQuickQuestionClick("What's the difference between an entity and a read-model?")}
          >
            What's the difference between an entity and a read-model?
          </button>
          <button
            className="bc-quick-question"
            onClick={() => onQuickQuestionClick('How to know when a reducer failed?')}
          >
            How to know when a reducer failed?
          </button>
          <button
            className="bc-quick-question"
            onClick={() => onQuickQuestionClick('Create a read-model and subscribe to it using websockets in Bash')}
          >
            Create a read-model and subscribe to it using websockets in Bash
          </button>
        </div>
      )}
      <ChatResponse questionId={questionId} response={response} loading={loading} hasFinished={hasFinished} />
    </div>
  )
}

export function AskAIBar({ handleKeyDown, loading, isModalStyle, hasFinished, resetSearchResponse }) {
  const inputRef = useRef(null)

  const handleResetClick = () => {
    inputRef.current.value = ''
    resetSearchResponse()
    inputRef.current.focus()
  }

  return (
    <div className={isModalStyle ? 'bc-searchbar max-width-100' : 'bc-searchbar'}>
      <PrivateGPTSearchIcon className="bc-searchbar-icon" />
      <input
        placeholder="Ask PrivateGPT about Booster"
        className="bc-input"
        type="text"
        onKeyDown={handleKeyDown}
        disabled={loading}
        ref={inputRef}
      />
      {hasFinished ? <ResetSearchButton resetSearchResponse={handleResetClick} /> : null}
    </div>
  )
}

export function ResetSearchButton({ resetSearchResponse }) {
  return (
    <button className="bc-reset-button" onClick={resetSearchResponse}>
      <img src="/img/cancel.svg" className="bc-reset-icon" alt="Reset" />
    </button>
  )
}

export function AskAIDisclaimer() {
  return <div className="bc-beta-disclaimer"> PrivateGPT · Free beta version</div>
}
