import React from 'react'
import NavbarNavLink from '@theme-original/NavbarItem/NavbarNavLink'

export default function NavbarNavLinkWrapper(props) {
  const handleClick = (customProps) => {
    if (customProps?.trackingEvent) {
      fathom.trackGoal(customProps.trackingEvent, 0)
    }
  }

  return (
    <div onClick={() => handleClick(props.customProps)}>
      <NavbarNavLink {...props} />
    </div>
  )
}
