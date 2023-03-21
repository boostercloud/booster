import React from 'react';

type CustomLinkProps = {
  href: string;
  onClick?: () => void;
  children: React.ReactNode;
};

const CustomLink = ({ href, onClick, children }: CustomLinkProps): JSX.Element => {
  const handleClick = (_event) => {
    if (onClick) {
      onClick(); // execute the custom onClick function
    }
  };

  return (
    <a href={href} target="_blank" onClick={handleClick}>
      {children}
    </a>
  )
}

export default CustomLink