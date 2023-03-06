import useBaseUrl from '@docusaurus/useBaseUrl'
import SearchIcon from '@site/static/img/search-icon.svg'
import Layout from '@theme/Layout'
import React, { useState } from 'react'
import { ChatResponse } from '../components/BoosterChat/ChatResponse'
import { CoolTitle } from '../components/BoosterChat/CoolTitle'
import { ChatService } from '../services/chat-service'

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

type BoosterChatProps = {
  embedded?: boolean;
};


export default function BoosterChat(props: BoosterChatProps): JSX.Element {
  const { embedded = false } = props;
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [interacted, setInteracted] = useState(false)

  const handleResponseUpdated = (newResponseFragment) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
  }

  const handleSearch = async (query: string) => {
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

  if (embedded) {
    return (
      <div className='bc-embedded'>
        <div className='bc-logo-container'>
          <img className='bc-ask-ai-logo-embedded' src={useBaseUrl('/img/ask-ai-logo.png')} alt="Ask AI Logo" />
        </div>
        <div className='bc-searchbar-embedded'>
          <SearchIcon className='bc-searchbar-icon-embedded'/>
          <input
            placeholder="What is Booster?"
            className="bc-input-embedded"
            type="text"
            onKeyDown={handleKeyDown}
            disabled={loading}
          />
        </div>
        <ChatResponse response={response} loading={loading} embedded={true}/>
      </div>
    )
    
  } else {
    return (
      <Layout wrapperClassName="bc-layout">
        <img className="bc-hero" src={useBaseUrl('/img/booster-logo.png')} alt="Booster Logo" />
        <CoolTitle hidden={interacted} />
        <div className="bc-logo-container">
          <img className="bc-ask-ai-logo" src={useBaseUrl('/img/ask-ai-logo.png')} alt="Ask AI Logo" />
        </div>
        <div className="bc-searchbar">
          <SearchIcon />
          <input
            placeholder="What is Booster?"
            className="bc-input"
            type="text"
            onKeyDown={handleKeyDown}
            onFocus={() => setInteracted(true)}
            disabled={loading}
          />
        </div>
        <ChatResponse response={response} loading={loading} embedded={false}/>
      </Layout>
    )
  }
}
