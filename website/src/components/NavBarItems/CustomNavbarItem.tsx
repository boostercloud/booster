import React, { useEffect } from 'react';
import Modal from 'react-modal';
import { ChatService } from '@site/src/services/chat-service';
import { useState } from 'react';
import { ChatResponse } from '../BoosterChat/ChatResponse';
import { AskAIBar } from '../BoosterChat/BoosterChat';
import { fathom } from 'docusaurus-plugin-fathom'

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: { imageURL: string, altText: string }): JSX.Element | null {
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

    fathom.trackGoal('UNOKECXW', 0)
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

  const closeModal = () => {
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
      <button onClick={openModal} type="button" className='navbar_custom_item--button'>
        <img src={props.imageURL} alt={props.altText} className='navbar_custom_item--image' />
      </button>
      <Modal
        isOpen={isModalOpen}
        onRequestClose={closeModal}
        style={{
          overlay: {
            backgroundColor: '#656C85CC',
            zIndex: 1000,
          },
          content: {
            top: '50%',
            left: '50%',
            right: 'auto',
            bottom: 'auto',
            marginRight: '-50%',
            transform: 'translate(-50%, -50%)',
            padding: '2rem',
            borderRadius: '1rem',
            maxHeight: '60%',
            width: 'min(100vw, 600px)'
          },
        }}
      >
        <AskAIBar handleKeyDown={handleKeyDown} setInteracted={setInteracted} loading={loading} isModalStyle={true} />
        <ChatResponse response={response} loading={loading} hasFinished={hasFinished} />
      </Modal>
    </>
  )
}
