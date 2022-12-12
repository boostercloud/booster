import React from 'react'
import styles from './styles.module.css'

interface TerminalWindowProps {
  children: React.ReactNode
}

export default function TerminalWindow({ children }: TerminalWindowProps): JSX.Element {
  return (
    <div className={styles.terminalWindow}>
      <div className={styles.terminalWindowHeader}>
        <div className={styles.buttons}>
          <span className={styles.dot} style={{ background: '#f25f58' }} />
          <span className={styles.dot} style={{ background: '#fbbe3c' }} />
          <span className={styles.dot} style={{ background: '#58cb42' }} />
        </div>
      </div>

      <div className={styles.terminalWindowBody}>{children}</div>
    </div>
  )
}
