import { useCallback, useState } from 'react'
import IntroOverlay from './components/IntroOverlay'
import OrganizationsPage from './pages/OrganizationsPage'
import './App.css'

function App() {
  const [showIntro, setShowIntro] = useState(true)
  const [mainVisible, setMainVisible] = useState(false)

  const handleIntroFadeStart = useCallback(() => {
    setMainVisible(true)
  }, [])

  const handleIntroComplete = useCallback(() => {
    setShowIntro(false)
  }, [])

  return (
    <div className="app-shell">
      {showIntro && (
        <IntroOverlay
          onFadeStart={handleIntroFadeStart}
          onComplete={handleIntroComplete}
        />
      )}
      <div
        className={`app-main${mainVisible ? ' app-main--visible' : ''}`}
        aria-hidden={!mainVisible}
      >
        <OrganizationsPage />
      </div>
    </div>
  )
}

export default App
