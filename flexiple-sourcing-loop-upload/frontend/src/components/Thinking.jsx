import React, { useEffect, useState } from 'react'

export default function Thinking({ steps }) {
  const [current, setCurrent] = useState(0)

  useEffect(() => {
    setCurrent(0)
    // backend answers in one shot, so the steps are on a timer - the last one
    // stays lit until the response lands
    const timer = setInterval(() => {
      setCurrent((c) => (c < steps.length - 1 ? c + 1 : c))
    }, 2600)
    return () => clearInterval(timer)
  }, [steps])

  return (
    <div>
      <div className="thinking">
        {steps.map((step, i) => (
          <div
            key={step}
            className={'think-step ' + (i < current ? 'done' : i === current ? 'active' : '')}
          >
            <span className="step-dot">{i < current ? '✓' : ''}</span>
            {step}
          </div>
        ))}
      </div>

      {[0, 1, 2].map((i) => (
        <div className="skeleton" key={i}>
          <div className="sk-line" style={{ width: '38%', height: 13 }} />
          <div className="sk-line" style={{ width: '62%' }} />
          <div className="sk-line" style={{ width: '88%' }} />
          <div className="sk-line" style={{ width: '45%' }} />
        </div>
      ))}
    </div>
  )
}
