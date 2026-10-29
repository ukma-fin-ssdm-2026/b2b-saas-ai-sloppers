import { useCallback, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppHeader from './components/AppHeader'
import IntroOverlay from './components/IntroOverlay'
import EventDetailPage from './pages/EventDetailPage'
import EventsPage from './pages/EventsPage'
import OrganizationDetailPage from './pages/OrganizationDetailPage'
import OrganizationsPage from './pages/OrganizationsPage'
import './App.css'

const INTRO_SEEN_KEY = 'community-intro-seen'

function hasSeenIntro(): boolean {
  try {
    return sessionStorage.getItem(INTRO_SEEN_KEY) === '1'
  } catch {
    return false
  }
}

function markIntroSeen(): void {
  try {
    sessionStorage.setItem(INTRO_SEEN_KEY, '1')
  } catch {
    // ignore quota / private mode
  }
}

function App() {
  const [showIntro, setShowIntro] = useState(() => !hasSeenIntro())
  const [mainVisible, setMainVisible] = useState(() => hasSeenIntro())

  const handleIntroFadeStart = useCallback(() => {
    setMainVisible(true)
  }, [])

  const handleIntroComplete = useCallback(() => {
    markIntroSeen()
    setShowIntro(false)
  }, [])

  return (
    <BrowserRouter>
      <div className="app-shell">
        {showIntro && (
          <IntroOverlay
            onFadeStart={handleIntroFadeStart}
            onComplete={handleIntroComplete}
          />
        )}
        <div
          className={`app-main${mainVisible ? ' app-main--visible' : ''}`}
          aria-hidden={showIntro ? !mainVisible : false}
        >
          <AppHeader />
          <Routes>
            <Route path="/" element={<Navigate to="/organizations" replace />} />
            <Route path="/organizations" element={<OrganizationsPage />} />
            <Route
              path="/organizations/:organizationId"
              element={<OrganizationDetailPage />}
            />
            <Route path="/events" element={<EventsPage />} />
            <Route path="/events/:eventId" element={<EventDetailPage />} />
            <Route path="*" element={<Navigate to="/organizations" replace />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}

export default App
