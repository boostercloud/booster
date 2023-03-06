import useBaseUrl from '@docusaurus/useBaseUrl'
import { ChatService } from '@site/src/services/chat-service'
import SearchIcon from '@site/static/img/search-icon.svg'
import React, { useState } from 'react'
import { ChatResponse } from './ChatResponse'
import { CoolTitle } from './CoolTitle'

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

export default function BoosterChat(): JSX.Element {
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [interacted, setInteracted] = useState(false)

  const handleResponseUpdated = (_question, newResponseFragment, _hasFinished) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
  }

  const handleSearch = async (query: string) => {
    if (query?.trim() === "" || query === null) {
      return
    }
    
    setLoading(true)
    setResponse('')
    ChatService.answerBoosterQuestion(query, handleResponseUpdated)
      .catch((error) => {
        setResponse(NO_RESPONSE)
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
    <div className='bc-embedded'>
      <CoolTitle hidden={interacted} />
      <div className='bc-logo-container'>
        <img className='bc-ask-ai-logo-embedded' src={useBaseUrl('/img/ask-ai-logo.png')} alt="Ask AI" />
      </div>
      <AskAIBar handleKeyDown={handleKeyDown} setInteracted={setInteracted} loading={loading} />
      <ChatResponse response={response} loading={loading} hasFinished={true}/>
    </div>
  )
}

export function AskAIBar({ handleKeyDown, setInteracted, loading }) {
  return (
    <div className='bc-searchbar-embedded'>
      <SearchIcon className='bc-searchbar-icon-embedded'/>
      <input
        placeholder="Ask me about Booster"
        className="bc-input-embedded"
        type="text"
        onKeyDown={handleKeyDown}
        onFocus={() => setInteracted(true)}
        disabled={loading}
      />
      <div className="bc-beta-disclaimer">Provisional free version</div>
    </div>
  )
}