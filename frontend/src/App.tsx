import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { LoadingBlock } from '@/components/ui/feedback'
import { ToastProvider } from '@/components/ui/toast'
import { AuthProvider, useAuth } from '@/hooks/useAuth'
import { AdminLayout } from '@/layouts/AdminLayout'
import { StudentLayout } from '@/layouts/StudentLayout'
const AdminDashboard = lazy(() => import('@/pages/admin/AdminDashboard'))
const AssignmentDetailPage = lazy(() => import('@/pages/admin/AssignmentDetailPage'))
const AssignmentFormPage = lazy(() => import('@/pages/admin/AssignmentFormPage'))
const AssignmentsPage = lazy(() => import('@/pages/admin/AssignmentsPage'))
const CourseDetailPage = lazy(() => import('@/pages/admin/CourseDetailPage'))
const CoursesPage = lazy(() => import('@/pages/admin/CoursesPage'))
const ExportsPage = lazy(() => import('@/pages/admin/ExportsPage'))
const GroupDetailPage = lazy(() => import('@/pages/admin/GroupDetailPage'))
const GroupsPage = lazy(() => import('@/pages/admin/GroupsPage'))
const QuestionBankDetailPage = lazy(() => import('@/pages/admin/QuestionBankDetailPage'))
const QuestionBanksPage = lazy(() => import('@/pages/admin/QuestionBanksPage'))
const QuizEditorPage = lazy(() => import('@/pages/admin/QuizEditorPage'))
const QuizzesPage = lazy(() => import('@/pages/admin/QuizzesPage'))
const StudentsPage = lazy(() => import('@/pages/admin/StudentsPage'))
const ChangePasswordPage = lazy(() => import('@/pages/public/ChangePasswordPage'))
const LandingPage = lazy(() => import('@/pages/public/LandingPage'))
const LoginPage = lazy(() => import('@/pages/public/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/public/RegisterPage'))
const NotFoundPage = lazy(() => import('@/pages/public/NotFoundPage'))
const AssignmentPage = lazy(() => import('@/pages/student/AssignmentPage'))
const ResultPage = lazy(() => import('@/pages/student/ResultPage'))
const StudentDashboard = lazy(() => import('@/pages/student/StudentDashboard'))
const SubmittedPage = lazy(() => import('@/pages/student/SubmittedPage'))
const TakeQuizPage = lazy(() => import('@/pages/student/TakeQuizPage'))
import type { Role } from '@/types/api'

function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <LoadingBlock />
  if (!user) return <Navigate to={`/login?role=${role.toLowerCase()}&next=${encodeURIComponent(location.pathname)}`} replace />
  if (user.role !== role) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/student'} replace />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Suspense fallback={<LoadingBlock />}>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/change-password" element={<ChangePasswordPage />} />

            <Route
              path="/admin"
              element={
                <RequireRole role="ADMIN">
                  <AdminLayout />
                </RequireRole>
              }
            >
              <Route index element={<AdminDashboard />} />
              <Route path="students" element={<StudentsPage />} />
              <Route path="courses" element={<CoursesPage />} />
              <Route path="courses/:id" element={<CourseDetailPage />} />
              <Route path="groups" element={<GroupsPage />} />
              <Route path="groups/:id" element={<GroupDetailPage />} />
              <Route path="question-banks" element={<QuestionBanksPage />} />
              <Route path="question-banks/:id" element={<QuestionBankDetailPage />} />
              <Route path="quizzes" element={<QuizzesPage />} />
              <Route path="quizzes/:id" element={<QuizEditorPage />} />
              <Route path="assignments" element={<AssignmentsPage />} />
              <Route path="assignments/new" element={<AssignmentFormPage />} />
              <Route path="assignments/:id" element={<AssignmentDetailPage />} />
              <Route path="exports" element={<ExportsPage />} />
            </Route>

            <Route
              path="/student"
              element={
                <RequireRole role="STUDENT">
                  <StudentLayout />
                </RequireRole>
              }
            >
              <Route index element={<StudentDashboard />} />
              <Route path="assignments/:id" element={<AssignmentPage />} />
              <Route path="attempts/:id/submitted" element={<SubmittedPage />} />
              <Route path="attempts/:id/result" element={<ResultPage />} />
            </Route>
            {/* The quiz page is full-screen, without the student chrome. */}
            <Route
              path="/student/attempts/:id"
              element={
                <RequireRole role="STUDENT">
                  <TakeQuizPage />
                </RequireRole>
              }
            />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
          </Suspense>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
