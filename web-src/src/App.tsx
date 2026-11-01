import { useCallback, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import AppHeader from './components/AppHeader'
import IntroOverlay from './components/IntroOverlay'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminLayout from './pages/admin/AdminLayout'
import AdminOrganizationDetailPage from './pages/admin/AdminOrganizationDetailPage'
import AdminOrganizationEventsPage from './pages/admin/AdminOrganizationEventsPage'
import AdminOrganizationsPage from './pages/admin/AdminOrganizationsPage'
import EventDetailPage from './pages/EventDetailPage'
import EventsPage from './pages/EventsPage'
import LabHomePage from './pages/lab/LabHomePage'
import LabLayout from './pages/lab/LabLayout'
import LabPlaygroundPage from './pages/lab/LabPlaygroundPage'
import LoginPage from './pages/LoginPage'
import OrganizationDetailPage from './pages/OrganizationDetailPage'
import OrganizationsPage from './pages/OrganizationsPage'
import RegisterPage from './pages/RegisterPage'
import VerifyPage from './pages/VerifyPage'
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
    // ignore
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
    <AuthProvider>
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
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/verify" element={<VerifyPage />} />

              <Route
                path="/admin"
                element={
                  <RequireAuth>
                    <AdminLayout />
                  </RequireAuth>
                }
              >
                <Route index element={<AdminHomePage />} />
                <Route
                  path="organizations"
                  element={<AdminOrganizationsPage />}
                />
                <Route
                  path="organizations/:organizationId"
                  element={<AdminOrganizationDetailPage />}
                />
                <Route
                  path="organizations/:organizationId/events"
                  element={<AdminOrganizationEventsPage />}
                />
              </Route>

              <Route
                path="/lab"
                element={
                  <RequireAuth>
                    <LabLayout />
                  </RequireAuth>
                }
              >
                <Route index element={<LabHomePage />} />
                <Route path="playground" element={<LabPlaygroundPage />} />
              </Route>

              <Route path="*" element={<Navigate to="/organizations" replace />} />
            </Routes>
          </div>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
