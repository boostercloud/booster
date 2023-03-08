import { ChatService } from '@site/src/services/chat-service'
import AskAISearchIcon from '@site/static/img/ask-ai-bubble.svg'
import React, { useEffect, useState } from 'react'
import { AnalyticsClient } from '../Analytics/analytics-client'
import { ChatResponse } from './ChatResponse'

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

export default function BoosterChat(): JSX.Element {
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [interacted, setInteracted] = useState(false)
  const [hasFinished, setHasFinished] = useState(false)

  useEffect(() => {
    AnalyticsClient.start()  
  }, [])

  const handleResponseUpdated = (_question, newResponseFragment, _hasFinished) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
    setHasFinished(_hasFinished)
  }

  const handleSearch = async (query: string) => {
    if (query?.trim() === "" || query === null) {
      return
    }
    
    AnalyticsClient.trackEvent('CEJF3EH1')
    setLoading(true)
    setResponse('')
    setHasFinished(false)

    ChatService.answerBoosterQuestion(query, handleResponseUpdated)
      .catch((error) => {
        setResponse(NO_RESPONSE)
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

  return (
    <div className='bc-layout'>
      <AskAIBar handleKeyDown={handleKeyDown} setInteracted={setInteracted} loading={loading} isModalStyle= {false}/>
      <AskAIDisclaimer/>
      <ChatResponse response={response} loading={loading} hasFinished={hasFinished}/>
    </div>
  )
}

export function AskAIBar({ handleKeyDown, setInteracted, loading, isModalStyle }) {
  return (
    <div className= {isModalStyle ? 'bc-searchbar max-width-100' : 'bc-searchbar'}>
      <AskAISearchIcon className='bc-searchbar-icon'/>
      <input
        placeholder="Ask AI anything about Booster"
        className="bc-input"
        type="text"
        onKeyDown={handleKeyDown}
        onFocus={() => setInteracted(true)}
        disabled={loading}
      />
    </div>
  )
}

export function AskAIDisclaimer() {
  return (<div className="bc-beta-disclaimer"> Ask AI · Temporary free version</div>)
}