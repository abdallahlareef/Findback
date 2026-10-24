import { useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

function App() {
  const [serial, setSerial] = useState('')
  const [mark, setMark] = useState('')
  const [accessory, setAccessory] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setMessage('')

    const normalizedSerial = serial.trim().toUpperCase()

    if (
      normalizedSerial !== '' &&
      !/^[A-Z0-9]{4}$/.test(normalizedSerial)
    ) {
      setError('Enter exactly 4 letters or digits, or leave the suffix blank.')
      return
    }

    setMessage(
      'Form check passed. Demo only: your evidence has not been saved or sent.'
    )
  }

  function clearFeedback() {
    setError('')
    setMessage('')
  }

  return (
    <main className="page">
      <header>
        <p className="brand">FINDBACK · MEMBER 2</p>
        <h1>Private evidence</h1>
        <p>Describe identifying details of the earbuds.</p>
      </header>

      <aside className="notice">
        <strong>Prototype — use invented details only.</strong>
        <p>
          This form is not connected to accounts or protected storage.
          Refreshing the page clears your entries.
        </p>
      </aside>

      <form onSubmit={handleSubmit}>
        <p>
          All fields are optional. Leave a detail blank if you do not know
          it or cannot safely inspect it. Do not enter passwords, unlock
          PINs or payment-card numbers.
        </p>

        <div className="field">
          <label htmlFor="serial">Serial suffix</label>
          <p id="serial-help" className="hint">
            Last 4 letters or digits, for example A82F.
          </p>
          <input
            id="serial"
            value={serial}
            onChange={(event) => {
              setSerial(event.target.value)
              clearFeedback()
            }}
            aria-describedby={
              error ? 'serial-help serial-error' : 'serial-help'
            }
            aria-invalid={Boolean(error)}
            autoComplete="off"
            spellCheck={false}
          />
          {error && (
            <p id="serial-error" className="error" role="alert">
              {error}
            </p>
          )}
        </div>

        <div className="field">
          <label htmlFor="mark">Private identifying mark</label>
          <p id="mark-help" className="hint">
            Describe a non-public mark or engraving, if known.
            Staff will review descriptions manually.
          </p>
          <textarea
            id="mark"
            rows={3}
            maxLength={500}
            value={mark}
            onChange={(event) => {
              setMark(event.target.value)
              clearFeedback()
            }}
            aria-describedby="mark-help"
          />
        </div>

        <div className="field">
          <label htmlFor="accessory">Distinctive accessory</label>
          <p id="accessory-help" className="hint">
            Describe an identifying accessory, if known. Maximum 500 characters.
          </p>
          <textarea
            id="accessory"
            rows={3}
            maxLength={500}
            value={accessory}
            onChange={(event) => {
              setAccessory(event.target.value)
              clearFeedback()
            }}
            aria-describedby="accessory-help"
          />
        </div>

        <button type="submit">Check sample evidence</button>

        <p className="status" role="status">
          {message}
        </p>
      </form>
    </main>
  )
}

export default App