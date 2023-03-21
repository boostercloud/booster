import React from 'react'
import CustomLink from './CustomLink';

type CustomLinkWrapperProps = {
    children: React.ReactNode
}
  
const CustomLinkWrapper: React.FC = ({ children }: CustomLinkWrapperProps) => {
  const href = 'https://example.com';
  const onClick = () => console.log('Clicked!');

  return (
    <CustomLink href={href} onClick={onClick}>
      {children}
    </CustomLink>
  )
}

export default CustomLinkWrapper;