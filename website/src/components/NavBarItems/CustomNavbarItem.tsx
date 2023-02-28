import React from 'react';
import { useState } from 'react';
import Modal from 'react-modal';

// see https://github.com/facebook/docusaurus/issues/7227
export default function CustomNavbarItem(props: {
  content: string;
  mobile?: boolean;
}): JSX.Element | null {
  const [modalOpen, setModalOpen] = useState(false);

  const openModal = () => {
    setModalOpen(true);
  };

  return (
    <>
      <button onClick={openModal} type="button">
        {props.content}
        {props.mobile ? ' (mobile)' : ''}
      </button>
      <Modal isOpen={modalOpen} onRequestClose={() => setModalOpen(false)}>
        <button onClick={() => setModalOpen(false)}>Close</button>
      </Modal>
    </>
  );
}
