import React, { useEffect, useMemo, useState } from 'react'
import { api } from './api.js'
import SearchScreen from './components/SearchScreen.jsx'
import Thinking from './components/Thinking.jsx'
import FiltersPanel from './components/FiltersPanel.jsx'
import RubricPanel from './components/RubricPanel.jsx'
import ProfileCard from './components/ProfileCard.jsx'
import ChatPanel from './components/ChatPanel.jsx'
import FrozenSummary from './components/FrozenSummary.jsx'

const SHOWN = 5

const SEARCH_STEPS = [
  'Reading your request',
  'Writing the filters and the fit rubric',
  'Filtering the talent map',
  'Scoring the survivors against the rubric',
]

const REFINE_STEPS = [
  'Working out what your feedback means for the role',
  'Adjusting the filters and the rubric',
  'Re-running the search',
]

// "1 and 4" / "1, 2 and 4"
function joinList(items) {
  if (items.length <= 1) return items.join('')
  return items.slice(0, -1).join(', ') + ' and ' + items[items.length - 1]
}

export default function App() {
  const [health, setHealth] = useState(null)
  const [session, setSession] = useState(null)
  const [busy, setBusy] = useState(null) // null | 'search' | 'refine'
  const [error, setError] = useState(null)
  const [lastAction, setLastAction] = useState(null)
  const [pendingFeedback, setPendingFeedback] = useState(null)

  // local edits, applied only when the recruiter hits Apply
  const [draftFilters, setDraftFilters] = useState(null)
  const [draftRubric, setDraftRubric] = useState(null)

  const [votes, setVotes] = useState({})
  const [showAll, setShowAll] = useState(false)

  useEffect(() => {
    api.health().then(setHealth)
  }, [])

  // new round from the server resets the drafts and the thumbs
  useEffect(() => {
    if (!session) return
    setDraftFilters(session.filters)
    setDraftRubric(session.rubric)
    setVotes({})
    setShowAll(false)
  }, [session?.round, session?.session_id])

  const dirty =
    session &&
    draftFilters &&
    (JSON.stringify(draftFilters) !== JSON.stringify(session.filters) ||
      JSON.stringify(draftRubric) !== JSON.stringify(session.rubric))

  const results = session?.results || []
  const visible = showAll ? results : results.slice(0, SHOWN)

  const voteSentence = useMemo(() => {
    const yes = []
    const no = []
    visible.forEach((item, i) => {
      if (votes[item.profile.id] === 'yes') yes.push(i + 1)
      if (votes[item.profile.id] === 'no') no.push(i + 1)
    })
    if (!yes.length && !no.length) return ''

    const parts = []
    if (yes.length) parts.push(`${joinList(yes)} ${yes.length > 1 ? 'are' : 'is'} right`)
    if (no.length) parts.push(`${joinList(no)} ${no.length > 1 ? 'are' : 'is'} not`)
    return parts.join(', ') + '.'
  }, [votes, visible])

  // each action remembers itself so Retry can replay it
  const run = async (kind, action, remember) => {
    setBusy(kind)
    setError(null)
    setLastAction(remember)
    try {
      const next = await action()
      setSession(next)
      setPendingFeedback(null)
    } catch (e) {
      setError({ message: e.message, retryable: e.retryable })
    } finally {
      setBusy(null)
    }
  }

  const doSearch = (query) => run('search', () => api.search(query), { kind: 'search', query })

  const doRefine = (feedback) => {
    setPendingFeedback(feedback)
    return run('refine', () => api.refine(session.session_id, feedback), { kind: 'refine', feedback })
  }

  const doApplyEdits = () =>
    run('refine', () => api.edit(session.session_id, draftFilters, draftRubric), {
      kind: 'edit',
      filters: draftFilters,
      rubric: draftRubric,
    })

  const retry = () => {
    if (!lastAction) return
    if (lastAction.kind === 'search') doSearch(lastAction.query)
    else if (lastAction.kind === 'refine') doRefine(lastAction.feedback)
    else doApplyEdits()
  }

  const doFreeze = () => run('refine', () => api.freeze(session.session_id), null)
  const doUnfreeze = () => run('refine', () => api.unfreeze(session.session_id), null)

  const reset = () => {
    setSession(null)
    setError(null)
    setLastAction(null)
    setPendingFeedback(null)
  }

  if (!session && busy === 'search') {
    return (
      <div className="landing">
        <div className="landing-inner">
          <Thinking steps={SEARCH_STEPS} />
        </div>
      </div>
    )
  }

  if (!session) {
    return (
      <SearchScreen
        onSearch={doSearch}
        health={health}
        error={error}
        onDismissError={() => setError(null)}
      />
    )
  }

  if (session.frozen) {
    return <FrozenSummary session={session} onReopen={doUnfreeze} onNewSearch={reset} />
  }

  const chat = pendingFeedback && busy
    ? [...session.chat, { role: 'recruiter', text: pendingFeedback }]
    : session.chat

  return (
    <div className="shell">
      <div className="topbar">
        <div className="query">
          <div className="query-label">Searching for</div>
          <div className="query-text">{session.query}</div>
        </div>

        <div className="topbar-actions">
          <span className="badge badge-accent">Round {session.round}</span>
          <span className="badge">
            {session.matched_count} of {session.total_profiles} matched
          </span>
          <button className="btn" onClick={reset}>
            New search
          </button>
          <button className="btn btn-primary" onClick={doFreeze} disabled={!!busy}>
            Freeze search
          </button>
        </div>
      </div>

      <div className="columns">
        {/* filters + rubric */}
        <div className="col-sticky">
          <FiltersPanel
            filters={draftFilters || session.filters}
            onChange={setDraftFilters}
            disabled={!!busy}
            matched={session.matched_count}
            total={session.total_profiles}
          />

          <RubricPanel
            rubric={draftRubric || session.rubric}
            onChange={setDraftRubric}
            disabled={!!busy}
          />

          {dirty && (
            <div className="card" style={{ marginTop: 14 }}>
              <div className="dirty-bar" style={{ borderTop: 'none', borderRadius: 'var(--radius-lg)' }}>
                <span>You changed the search by hand.</span>
                <span style={{ display: 'flex', gap: 6 }}>
                  <button
                    className="btn btn-sm"
                    onClick={() => {
                      setDraftFilters(session.filters)
                      setDraftRubric(session.rubric)
                    }}
                  >
                    Undo
                  </button>
                  <button className="btn btn-sm btn-primary" onClick={doApplyEdits} disabled={!!busy}>
                    Apply & re-run
                  </button>
                </span>
              </div>
            </div>
          )}
        </div>

        {/* results */}
        <div>
          <div className="results-head">
            <h2>{results.length === 0 ? 'No matches' : `Top ${Math.min(SHOWN, results.length)} of ${results.length}`}</h2>
            <span className="meta">
              {session.matched_count} profile{session.matched_count === 1 ? '' : 's'} passed the filters,
              ranked against the rubric
            </span>
          </div>

          {error && (
            <div className="error-banner">
              <span>⚠</span>
              <span className="grow">{error.message}</span>
              {error.retryable && lastAction && (
                <button className="btn btn-sm" onClick={retry} disabled={!!busy}>
                  Retry
                </button>
              )}
              <button className="btn btn-sm" onClick={() => setError(null)}>
                Dismiss
              </button>
            </div>
          )}

          {busy === 'refine' && <Thinking steps={REFINE_STEPS} />}

          {busy !== 'refine' && results.length === 0 && (
            <div className="notice notice-empty">
              <h3>Nothing in the talent map matches these filters</h3>
              <p>
                {session.matched_count === 0
                  ? 'The objective filters ruled out all ' +
                    session.total_profiles +
                    ' profiles before scoring even started. Usually one filter is doing all the damage — a mandatory skill, or a years range that is too narrow.'
                  : 'The filters matched people but nothing came back ranked.'}
              </p>
              <div className="notice-actions">
                <button
                  className="btn"
                  onClick={() => doRefine('Nothing matched. Loosen the filters — drop whichever one is most likely ruling people out — and tell me which one you dropped.')}
                  disabled={!!busy}
                >
                  Ask me to loosen the filters
                </button>
                <button
                  className="btn"
                  onClick={() => {
                    setDraftFilters({ ...draftFilters, required_skills: [] })
                  }}
                  disabled={!!busy}
                >
                  Clear mandatory skills
                </button>
              </div>
            </div>
          )}

          {busy !== 'refine' &&
            visible.map((item, i) => (
              <ProfileCard
                key={item.profile.id}
                item={item}
                rank={i + 1}
                delay={i * 45}
                vote={votes[item.profile.id]}
                onVote={(v) => setVotes({ ...votes, [item.profile.id]: v })}
              />
            ))}

          {busy !== 'refine' && results.length > SHOWN && (
            <button className="more-toggle" onClick={() => setShowAll(!showAll)}>
              {showAll
                ? `Show only the top ${SHOWN}`
                : `Show the other ${results.length - SHOWN} ranked candidate${
                    results.length - SHOWN === 1 ? '' : 's'
                  }`}
            </button>
          )}
        </div>

        {/* conversation */}
        <div className="col-sticky">
          <ChatPanel
            chat={chat}
            onSend={doRefine}
            busy={busy === 'refine'}
            frozen={session.frozen}
            voteSentence={voteSentence}
          />
        </div>
      </div>
    </div>
  )
}
