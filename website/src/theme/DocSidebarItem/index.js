import React from 'react';
import DocSidebarItem from '@theme-original/DocSidebarItem';

export default function DocSidebarItemWrapper(props) {
  const handleClick = (customProps) => {
    if (customProps && customProps.trackingEvent) {
      fathom.trackGoal(customProps.trackingEvent, 0);
      console.log(`DocSidebarItem clicked with trackingEvent: ${customProps.trackingEvent}`);
    }
  };

  return (
    <div onClick={() => handleClick(props.item.customProps)}>
      <DocSidebarItem {...props} />
    </div>
  );
}

