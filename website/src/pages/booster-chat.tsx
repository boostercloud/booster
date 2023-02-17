import useBaseUrl from '@docusaurus/useBaseUrl'
import SearchIcon from '@site/static/img/search-icon.svg'
import Layout from '@theme/Layout'
import React, { useState } from 'react'
import { ChatResponse } from '../components/booster-chat/ChatResponse'
import { CoolTitle } from '../components/booster-chat/CoolTitle'
import { ChatSerivce } from '../services/chat-service'

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

export default function BoosterChat(): JSX.Element {
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [interacted, setInteracted] = useState(false)

  const handleResponseUpdated = (newResponseFragment) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
  }

  const handleSearch = async (query: string) => {
    setLoading(true)
    setResponse('')
    ChatSerivce.answerBoosterQuestion(query, handleResponseUpdated)
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
    <Layout wrapperClassName="bc-layout">
      <img className="bc-hero" src={useBaseUrl('/img/booster-logo.png')} alt="Booster Logo" />
      <CoolTitle hidden={interacted} />
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
      <ChatResponse response={response} loading={loading} />
    </Layout>
  )
}
