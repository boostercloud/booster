import React from 'react'
import DocSidebarItem from '@theme-original/DocSidebarItem'

export default function DocSidebarItemWrapper(props) {
  const handleClick = (customProps) => {
    if (customProps?.trackingEvent) {
      fathom.trackGoal(customProps.trackingEvent, 0)
    }
  }

  return (
    <div onClick={() => handleClick(props.item.customProps)}>
      <DocSidebarItem {...props} />
    </div>
  )
}
