import React from 'react';

function CustomSidebarItem({ item }) {
  const handleClick = () => {
    if (customProps && customProps.trackingEvent) {
      fathom.trackGoal(customProps.trackingEvent, 0);
    }
  };

  return (
    <a className="menu__link" href={item.href} onClick={handleClick}>
      {item.label}
    </a>
  );
}

export default CustomSidebarItem;