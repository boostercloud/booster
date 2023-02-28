import SearchIcon from '@site/static/img/search-icon.svg'
import React from 'react';
import Modal from 'react-modal';
import { ChatSerivce } from '@site/src/services/chat-service';
import { useState } from 'react';
import { ChatResponse } from '../BoosterChat/ChatResponse';

const NO_RESPONSE = 'Sorry, I don`t know how to help with that.'

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: { content: string }): JSX.Element | null {
  const [modalOpen, setModalOpen] = useState(false);
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
  const openModal = () => {
    setModalOpen(modalOpen => !modalOpen)
  };

  return (
    <>
      <button onClick={openModal} type="button">
        {props.content}
      </button>
      <Modal
        isOpen={modalOpen}
        onRequestClose={() => setModalOpen(false)}
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
            borderRadius: '1rem'
          },
        }}
      >
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
      </Modal>
    </>
  )
}
