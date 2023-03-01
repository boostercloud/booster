import SearchIcon from '@site/static/img/search-icon.svg'
import React from 'react';
import Modal from 'react-modal';
import { ChatService } from '@site/src/services/chat-service';
import { useState } from 'react';
import { ChatResponse } from '../BoosterChat/ChatResponse';

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: { content: string }): JSX.Element | null {
  const [modalOpen, setModalOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState(null)
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [hasFinished, setHasFinished] = useState(false)
  const [interacted, setInteracted] = useState(false)
    
  const handleResponseUpdated = (question, newResponseFragment, hasFinished) => {
    // TODO: Find a way to stop receiving updates from the readable stream in
    // `ChatService.answerBoosterQuestion()`
    
    // if (searchQuery === question) {
      setResponse((prev) => `${prev}${newResponseFragment}`)
      setHasFinished(hasFinished)
    // }
  }

  const handleSearch = async (query: string) => {
    setLoading(true)
    setResponse('')
    setSearchQuery(query)
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
  const openModal = () => {
    setModalOpen(modalOpen => !modalOpen)
  }

  const closeModal= () => {
    setSearchQuery(null)
    setResponse(null)
    setLoading(null)
    setModalOpen(false)
  }

  return (
    <>
      <button onClick={openModal} type="button">
        {props.content}
      </button>
      <Modal
        isOpen={modalOpen}
        onRequestClose={closeModal}
        style={{
          overlay: {
            backgroundColor: 'rgba(0, 0, 0, 0.8)',
            zIndex: 1000,
          },
          content: {
            top: '50%',
            left: '50%',
            right: 'auto',
            bottom: 'auto',
            marginRight: '-50%',
            transform: 'translate(-50%, -50%)',
            maxWidth: '600px',
            padding: '2rem',
            borderRadius: '1rem',
            maxHeight: '60%',
            minWidth: '35%'
          },
        }}
      >
        <div className="bc-searchbar">
          <SearchIcon />
          <input
            placeholder="What is Booster?"
            className="bc-searchinput"
            type="text"
            onKeyDown={handleKeyDown}
            onFocus={() => setInteracted(true)}
            disabled={loading}
            autoFocus
          />
        </div>
        <ChatResponse response={response} loading={loading} hasFinished={hasFinished}/>
      </Modal>
    </>
  )
}
