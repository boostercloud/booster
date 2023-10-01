import React, { useEffect } from 'react'
import Modal from 'react-modal'
import { ChatService } from '../chat-service'
import { useState } from 'react'
import { ChatResponse } from '../../components/BoosterChat/ChatResponse'
import { AskAIBar, AskAIDisclaimer, ASK_AI_ERROR } from '../../components/BoosterChat/BoosterChat'
import { AnalyticsClient } from '../../components/Analytics/analytics-client'

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: { imageURL: string; altText: string }): JSX.Element | null {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState<string | null>(null)
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(null)
  const [hasFinished, setHasFinished] = useState(false)
  const [questionId, setQuestionId] = useState<string | null>(null)

  const handleResponseUpdated = (_questionId, question, newResponseFragment, hasFinished) => {
    setResponse((prev) => `${prev}${newResponseFragment}`)
    setHasFinished(hasFinished)
    setQuestionId(_questionId)
  }

  const handleSearch = async (query: string, controller: AbortController) => {
    if (query?.trim() === '') {
      return
    }

    AnalyticsClient.trackEvent('UNOKECXW')
    setLoading(true)
    setResponse('')
    setSearchQuery(query)
    setHasFinished(false)

    ChatService.answerBoosterQuestion(query, handleResponseUpdated, controller.signal)
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
    AnalyticsClient.start()
  }, [])

  useEffect(() => {
    const abortController = new AbortController()
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
    document.body.style.overflow = isModalOpen ? 'hidden' : ''

    return () => {
      document.body.style.overflow = ''
    }
  }, [isModalOpen])

  return (
    <>
      <a onClick={openModal} href="javascript:void(0)" className="navbar_custom_item--button">
        <img src={props.imageURL} alt={props.altText} className="navbar_custom_item--image" />
      </a>
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
            width: 'min(100vw, 600px)',
          },
        }}
      >
        <AskAIBar
          handleKeyDown={handleKeyDown}
          loading={loading}
          isModalStyle={true}
          hasFinished={hasFinished}
          resetSearchResponse={null}
        />
        <AskAIDisclaimer />
        <ChatResponse questionId={questionId} response={response} loading={loading} hasFinished={hasFinished} />
      </Modal>
    </>
  )
}
