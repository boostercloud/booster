import React from 'react';

function CustomSidebarItem({ item }) {
  const handleClick = () => {
    console.log('CustomSidebarItem clicked:', item.label);
  };

  return (
    <a className="menu__link" href={item.href} onClick={handleClick}>
      {item.label}
    </a>
  );
}

export default CustomSidebarItem;