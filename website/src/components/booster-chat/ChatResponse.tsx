import React, { FC } from 'react'
import ReactMarkdown from 'react-markdown'

interface ChatResponseProps {
  loading: boolean
  response: string | null
}

export const ChatResponse: FC<ChatResponseProps> = ({ loading, response }) => {
  if (response === null) {
    return <div></div>
  }

  if (!response.length) {
    return (
      <div className="bc-chat --loading">
        <span className="bc-loader"></span>
      </div>
    )
  }

  return (
    <>
      <div className="bc-chat">
        <ReactMarkdown>{response}</ReactMarkdown>
      </div>
      {!loading && (
        <ReactMarkdown>
          Not the answer you expected? We will be greatful to answer your question on the
          [#booster-help](https://discord.com/channels/763753198388510780/1019895895325675550) channel on Discord 🤗
        </ReactMarkdown>
      )}
    </>
  )
}
