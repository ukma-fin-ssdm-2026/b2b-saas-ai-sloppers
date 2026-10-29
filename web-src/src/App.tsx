import { useCallback, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import IntroOverlay from './components/IntroOverlay'
import EventDetailPage from './pages/EventDetailPage'
import EventsPage from './pages/EventsPage'
import OrganizationDetailPage from './pages/OrganizationDetailPage'
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
          aria-hidden={!mainVisible}
        >
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
