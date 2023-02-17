import React from 'react';
import NavbarNavLink from '@theme-original/NavbarItem/NavbarNavLink';

export default function NavbarNavLinkWrapper(props) {
  const handleClick = (customProps) => {
    console.log('NavbarNavLinkWrapper clicked', customProps);
    if (customProps && customProps.trackingEvent) {
      fathom.trackGoal(customProps.trackingEvent, 0);
      console.log(`Navbar link clicked with trackingEvent: ${customProps.trackingEvent}`);
    }
  };

  return (
    <div onClick={() => handleClick(props.customProps)}>
      <NavbarNavLink {...props} />
    </div>
  );
}
