import { useCallback, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import AppHeader from './components/AppHeader'
import IntroOverlay from './components/IntroOverlay'
import ThemeBackground from './components/ThemeBackground'
import { ThemeProvider } from './theme/ThemeContext'
import './theme/themes.css'
import AdminEventsPage from './pages/admin/AdminEventsPage'
import AdminHomePage from './pages/admin/AdminHomePage'
import AdminLayout from './pages/admin/AdminLayout'
import AdminModerationPage from './pages/admin/AdminModerationPage'
import AdminOrganizationDetailPage from './pages/admin/AdminOrganizationDetailPage'
import AdminOrganizationEventsPage from './pages/admin/AdminOrganizationEventsPage'
import AdminOrganizationsPage from './pages/admin/AdminOrganizationsPage'
import EventDetailPage from './pages/EventDetailPage'
import EventsPage from './pages/EventsPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import OrganizationDetailPage from './pages/OrganizationDetailPage'
import OrganizationsPage from './pages/OrganizationsPage'
import RegisterPage from './pages/RegisterPage'
import VerifyPage from './pages/VerifyPage'
import BoardLayout from './pages/board/BoardLayout'
import BoardListPage from './pages/board/BoardListPage'
import AdvertCreatePage from './pages/board/AdvertCreatePage'
import AdvertDetailPage from './pages/board/AdvertDetailPage'
import ProfilePage from './pages/ProfilePage'
import PasswordResetPage from './pages/PasswordResetPage'
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
      <ThemeProvider>
        <BrowserRouter>
          <ThemeBackground />
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
                <Route path="/" element={<HomePage />} />
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
                <Route path="/password-reset" element={<PasswordResetPage />} />
                <Route
                  path="/profile"
                  element={
                    <RequireAuth>
                      <ProfilePage />
                    </RequireAuth>
                  }
                />

                <Route
                  path="/board"
                  element={
                    <RequireAuth>
                      <BoardLayout />
                    </RequireAuth>
                  }
                >
                  <Route index element={<BoardListPage scope="open" />} />
                  <Route path="mine" element={<BoardListPage scope="mine" />} />
                  <Route
                    path="replied"
                    element={<BoardListPage scope="replied" />}
                  />
                  <Route path="new" element={<AdvertCreatePage />} />
                  <Route path=":advertId" element={<AdvertDetailPage />} />
                </Route>

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
                  <Route path="events" element={<AdminEventsPage />} />
                  <Route path="moderation" element={<AdminModerationPage />} />
                </Route>

                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </div>
          </div>
        </BrowserRouter>
      </ThemeProvider>
    </AuthProvider>
  )
}

export default App
