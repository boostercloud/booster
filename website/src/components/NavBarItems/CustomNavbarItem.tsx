import SearchIcon from '@site/static/img/search-icon.svg'
import React, { useEffect } from 'react';
import Modal from 'react-modal';
import { ChatService } from '@site/src/services/chat-service';
import { useState } from 'react';
import { ChatResponse } from '../BoosterChat/ChatResponse';

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: { content: string }): JSX.Element | null {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState<string | null>(null)
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [hasFinished, setHasFinished] = useState(false)
  const [interacted, setInteracted] = useState(false)
    
  const handleResponseUpdated = (question, newResponseFragment, hasFinished) => {
      setResponse((prev) => `${prev}${newResponseFragment}`)
      setHasFinished(hasFinished)
  }

  const handleSearch = async (query: string, controller: AbortController) => {
    if (query?.trim() === "" || query === null) {
      return
    }

    setLoading(true)
    setResponse('')
    setSearchQuery(query)
    setHasFinished(false)

    ChatService.answerBoosterQuestion(query, handleResponseUpdated, controller.signal)
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
      setSearchQuery(event.target.value)
    }
  }
  const openModal = () => {
    setResponse(null)
    setIsModalOpen(true)
  }

  const closeModal= () => {
    setSearchQuery(null)
    setResponse(null)
    setLoading(null)
    setIsModalOpen(false)
  }

  useEffect(() => {
    const abortController = new AbortController();
    handleSearch(searchQuery, abortController)
  
    const modalElement = document.querySelector('.modal')
  
    if (modalElement) {
      modalElement.addEventListener('Modal.afterClose', abortController.abort)
    }
  
    return () => {
      if (modalElement) {
        modalElement.removeEventListener('Modal.afterClose', abortController.abort)
      }
      abortController.abort()
    }
  }, [searchQuery])
  
  useEffect(() => {
    document.body.style.overflow = isModalOpen ? "hidden" : ""
  
    return () => {
      document.body.style.overflow = ""
    }
  }, [isModalOpen])

  return (
    <>
      <button onClick={openModal} type="button">
        {props.content}
      </button>
      <Modal
        isOpen={isModalOpen}
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
