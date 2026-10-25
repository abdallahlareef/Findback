import { useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type EvidenceFormProps = {
  role: 'claimant' | 'finder'
}

type VersionResponse = {
  versionNumber: number
}

const claimantId = 'demo-claimant-ui'
const foundItemId = 'demo-found-item-ui'

async function readResponse(response: Response): Promise<VersionResponse> {
  const text = await response.text()

  if (!response.ok) {
    throw new Error(text || `Request failed (${response.status}).`)
  }

  return JSON.parse(text) as VersionResponse
}

function EvidenceForm({ role }: EvidenceFormProps) {
  const [serial, setSerial] = useState('')
  const [mark, setMark] = useState('')
  const [accessory, setAccessory] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [supplementPending, setSupplementPending] = useState(false)
  const [reportId] = useState(() => `ui-demo-${crypto.randomUUID()}`)

  function clearFeedback() {
    setError('')
    setMessage('')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearFeedback()

    if (saved && !supplementPending) {
      setError('Request a supplement before submitting a correction.')
      return
    }

    const normalizedSerial = serial.trim().toUpperCase()

    if (normalizedSerial !== '' && !/^[A-Z0-9]{4}$/.test(normalizedSerial)) {
      setError('Enter exactly 4 letters or digits, or leave the suffix blank.')
      return
    }

    setBusy(true)

    try {
      const isBaseline = !saved
      const url = isBaseline
        ? '/api/member2/evidence/baseline'
        : `/api/member2/evidence/${encodeURIComponent(reportId)}/corrections`

      const body = isBaseline
        ? {
            reportId,
            claimantId,
            foundItemId,
            provenance: role === 'claimant' ? 'CLAIMANT' : 'FINDER',
            serialSuffix: normalizedSerial || null,
            privateMark: mark.trim() || null,
            accessoryDescription: accessory.trim() || null,
          }
        : {
            serialSuffix: normalizedSerial || null,
            privateMark: mark.trim() || null,
            accessoryDescription: accessory.trim() || null,
            reason: 'Updated through the demo form',
          }

      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })

      const result = await readResponse(response)
      setSaved(true)
      setSupplementPending(false)
      setMessage(`Demo evidence saved as version ${result.versionNumber}.`)
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : 'Could not reach the backend. Check that it is running.'
      )
    } finally {
      setBusy(false)
    }
  }

  async function handleSupplementRequest() {
    clearFeedback()
    setBusy(true)

    try {
      const response = await fetch(
        `/api/member2/evidence/${encodeURIComponent(reportId)}/supplement-requests`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            claimantId,
            foundItemId,
            requestId: crypto.randomUUID(),
          }),
        }
      )

      const result = await readResponse(response)
      setSupplementPending(true)
      setMessage(
        `Supplement requested. You can now submit a correction to version ${result.versionNumber}.`
      )
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : 'Could not reach the backend. Check that it is running.'
      )
    } finally {
      setBusy(false)
    }
  }

  const fieldsDisabled = busy || (saved && !supplementPending)

  return (
    <main className="page">
      <header>
        <p className="brand">FINDBACK · MEMBER 2</p>
        <h1>
          {role === 'claimant'
            ? 'Your ownership evidence'
            : 'Your finder observations'}
        </h1>
        <p>Describe identifying details of the earbuds.</p>
      </header>

      <aside className="notice">
        <strong>Prototype — use invented details only.</strong>
        <p>
          This form sends demo evidence to your local backend. Refreshing clears
          the form; saved demo versions remain in the local database.
        </p>
      </aside>

      <form onSubmit={handleSubmit}>
        <p>
          All fields are optional. Leave a detail blank if you do not know it
          or cannot safely inspect it. Do not enter passwords, unlock PINs or
          payment-card numbers.
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
            aria-describedby={error ? 'serial-help serial-error' : 'serial-help'}
            aria-invalid={Boolean(error)}
            autoComplete="off"
            spellCheck={false}
            disabled={fieldsDisabled}
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
            Describe a non-public mark or engraving, if known. Staff will
            review descriptions manually.
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
            disabled={fieldsDisabled}
          />
        </div>

        <div className="field">
          <label htmlFor="accessory">Distinctive accessory</label>
          <p id="accessory-help" className="hint">
            Describe an identifying accessory, if known. Maximum 500
            characters.
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
            disabled={fieldsDisabled}
          />
        </div>

        {!saved && (
          <button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save demo evidence'}
          </button>
        )}

        {saved && !supplementPending && (
          <button
            type="button"
            onClick={handleSupplementRequest}
            disabled={busy}
          >
            {busy ? 'Requesting…' : 'Request supplement'}
          </button>
        )}

        {saved && supplementPending && (
          <button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Submit correction'}
          </button>
        )}

        <p className="status" role="status">
          {message}
        </p>
      </form>
    </main>
  )
}

export default EvidenceForm