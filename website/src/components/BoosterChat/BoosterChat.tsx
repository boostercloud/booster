import useBaseUrl from '@docusaurus/useBaseUrl'
import { ChatService } from '@site/src/services/chat-service'
import SearchIcon from '@site/static/img/search-icon.svg'
import React, { useState } from 'react'
import { ChatResponse } from './ChatResponse'
import { CoolTitle } from './CoolTitle'
import { fathom } from 'docusaurus-plugin-fathom'
const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

export default function BoosterChat(): JSX.Element {
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [interacted, setInteracted] = useState(false)
  const [hasFinished, setHasFinished] = useState(false)

  const handleResponseUpdated = (_question, newResponseFragment, _hasFinished) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
    setHasFinished(_hasFinished)
  }

  const handleSearch = async (query: string) => {
    if (query?.trim() === "" || query === null) {
      return
    }
    
    fathom.trackGoal('CEJF3EH1', 0)
    setLoading(true)
    setResponse('')
    setHasFinished(false)

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
    <div className='bc-layout'>
      <CoolTitle hidden={interacted} />
      <div className='bc-logo-container'>
        <img className='bc-ask-ai-logo' src={useBaseUrl('/img/ask-ai-logo.png')} alt="Ask AI" />
      </div>
      <AskAIBar handleKeyDown={handleKeyDown} setInteracted={setInteracted} loading={loading} isModalStyle= {false}/>
      <ChatResponse response={response} loading={loading} hasFinished={hasFinished}/>
    </div>
  )
}

export function AskAIBar({ handleKeyDown, setInteracted, loading, isModalStyle }) {
  return (
    <div className= {isModalStyle ? 'bc-searchbar max-width-100' : 'bc-searchbar'}>
      <SearchIcon className='bc-searchbar-icon'/>
      <input
        placeholder="Ask me about Booster"
        className="bc-input"
        type="text"
        onKeyDown={handleKeyDown}
        onFocus={() => setInteracted(true)}
        disabled={loading}
      />
      <div className="bc-beta-disclaimer">Provisional free version</div>
    </div>
  )
}