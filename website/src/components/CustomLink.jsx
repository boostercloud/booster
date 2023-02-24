import React from 'react';

// A custom link react component was needed because the default onClick function
// of the <a> tag in the .mdx files was not working. This component is used
// in the mdx file to create a link that opens in a new tab and executes
// the custom onClick function.
const CustomLink = ({ href, onClick, children }) => {
    const handleClick = (event) => {
      if (onClick) {
        onClick(); // execute the custom onClick function
      }
    };
  
    return (
        <a href={href} target="_blank" onClick={handleClick}>
            {children}
      </a>
    );
};

export default CustomLink;