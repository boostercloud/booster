import React, { FC, useEffect, useMemo, useState } from 'react'

const MIN_SPEED = 1000

enum Direction {
  FORWARD = 'FORWARD',
  BACKWARD = 'BACKWARD',
}

interface CoolTitleProps {
  hidden: boolean
}

export const CoolTitle: FC<CoolTitleProps> = ({ hidden }) => {
  const AWESOME_TITLES = [
    'Hi! I am Booster assistant, nice to meet you!',
    'Ask me anything about Booster',
    'I am here to help you :)',
  ]
  const [cursor, setCursor] = useState({ titleIndex: 0, charIndex: 0, charDir: Direction.FORWARD })
  const title = useMemo(() => {
    const { titleIndex, charIndex } = cursor
    return AWESOME_TITLES[titleIndex].substring(0, charIndex)
  }, [cursor])

  const calculateNextCursor = (prev) => {
    const { titleIndex, charIndex, charDir } = prev
    const currentTitle = AWESOME_TITLES[titleIndex]

    if (charDir === Direction.FORWARD && charIndex === currentTitle.length) {
      return { ...prev, charIndex: charIndex + 1, charDir: Direction.BACKWARD }
    }

    if (charDir === Direction.BACKWARD && charIndex === 0) {
      return {
        ...prev,
        titleIndex: (titleIndex + 1) % AWESOME_TITLES.length,
        charDir: Direction.FORWARD,
      }
    }

    const nextCharIdx = charDir === Direction.FORWARD ? charIndex + 1 : charIndex - 1
    return { ...prev, charIndex: nextCharIdx }
  }

  const calcultateCursorSpeed = (cursor) => {
    const { titleIndex, charIndex, charDir } = cursor
    const titleLength = AWESOME_TITLES[titleIndex].length

    if (charDir === Direction.FORWARD) {
      return charIndex === titleLength ? MIN_SPEED : MIN_SPEED / (charIndex + 1)
    } else {
      return MIN_SPEED / (titleLength - charIndex)
    }
  }

  useEffect(() => {
    const cursorSpeed = calcultateCursorSpeed(cursor)
    setTimeout(() => {
      setCursor(calculateNextCursor)
    }, cursorSpeed)
  }, [cursor])

  return (
    <div style={{ height: hidden ? 0 : '25px', overflow: 'hidden' }} className="bc-title">
      <h2 style={{ margin: 0, fontSize: 18, fontStyle: 'italic' }}>
        {title}
        <span className="bc-writing-cursor"></span>
      </h2>
    </div>
  )
}
