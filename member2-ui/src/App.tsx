import { useState } from 'react'
import EvidenceForm from './EvidenceForm'

function App() {
  const [role, setRole] = useState<'claimant' | 'finder'>('claimant')

  return (
    <>
      <nav aria-label="Prototype role">
        <button type="button" onClick={() => setRole('claimant')}>
          Preview claimant form
        </button>
        <button type="button" onClick={() => setRole('finder')}>
          Preview finder form
        </button>
      </nav>

      <EvidenceForm role={role} />
    </>
  )
}

export default App