// Types mirroring the backend DTOs (backend/src/main/java/com/quizsphere/dto).

export type Role = 'ADMIN' | 'STUDENT'

export interface CourseRef {
  id: number
  courseCode: string
  courseName: string
}

export interface StudentRef {
  id: number
  registerNumber: string
  fullName: string
}

export interface StudentProfile {
  id: number
  registerNumber: string
  fullName: string
  academicYear: number
  department: string
  section?: string
  program?: string
  semester?: number
  courses: CourseRef[]
}

export interface UserInfo {
  id: number
  name: string
  email?: string
  role: Role
  mustChangePassword: boolean
  student?: StudentProfile
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: UserInfo
}

export interface AuthConfig {
  studentSelfRegistration: boolean
  adminRegistration: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Student {
  id: number
  registerNumber: string
  fullName: string
  email?: string
  academicYear: number
  department: string
  section?: string
  program?: string
  semester?: number
  active: boolean
  courses: CourseRef[]
  createdAt: string
}

export interface AttributeOptions {
  departments: string[]
  sections: string[]
  programs: string[]
}

export type RowAction = 'CREATE' | 'UPDATE' | 'SKIP_EXISTING' | 'INVALID'

export interface RosterImportRow {
  rowNumber: number
  registerNumber?: string
  fullName?: string
  action: RowAction
  errors: string[]
  warnings: string[]
  values: Record<string, string>
}

export interface RosterImportResult {
  fileName: string
  committed: boolean
  totalRows: number
  toCreate: number
  toUpdate: number
  skippedExisting: number
  invalid: number
  headerErrors: string[]
  rows: RosterImportRow[]
}

export interface MembershipResult {
  added: number
  alreadyPresent: number
  notFound: string[]
}

export interface Course {
  id: number
  courseCode: string
  courseName: string
  semester?: number
  studentCount: number
  createdAt: string
}

export type GroupType = 'ACADEMIC' | 'COURSE_BASED' | 'CUSTOM'
export type MembershipMode = 'DYNAMIC' | 'MANUAL'

export interface GroupFilter {
  academicYears?: number[]
  departments?: string[]
  sections?: string[]
  programs?: string[]
  semesters?: number[]
  courseIds?: number[]
  courseMatch?: 'ANY' | 'ALL'
}

export interface Group {
  id: number
  name: string
  description?: string
  groupType: GroupType
  membershipMode: MembershipMode
  filter?: GroupFilter
  filterSummary: string
  memberCount: number
  ownerName: string
  owned: boolean
  usedByAssignments: boolean
  createdAt: string
}

export interface GroupDetail {
  group: Group
  members: Student[]
}

export type QuizStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface QuizSummary {
  id: number
  title: string
  course?: CourseRef
  durationMinutes: number
  status: QuizStatus
  questionCount: number
  totalPoints: number
  updatedAt: string
  publishedAt?: string
}

export interface OptionAdmin {
  id: number
  text: string
  correct: boolean
}

export interface QuestionAdmin {
  id: number
  text: string
  points: number
  position: number
  explanation?: string
  options: OptionAdmin[]
  sourceQuestionId?: number
}

export interface QuizDetail {
  id: number
  title: string
  description?: string
  instructions?: string
  course?: CourseRef
  durationMinutes: number
  status: QuizStatus
  shuffleQuestions: boolean
  shuffleOptions: boolean
  questions: QuestionAdmin[]
  totalPoints: number
  editable: boolean
  hasAssignments: boolean
  createdAt: string
  updatedAt: string
  publishedAt?: string
}

export interface ValidationProblem {
  questionId?: number
  position?: number
  message: string
}

export interface QuestionImportRow {
  rowNumber: number
  question?: string
  options: string[]
  correctAnswer?: string
  marks?: number
  explanation?: string
  errors: string[]
  warnings: string[]
}

export interface QuestionImportResult {
  fileName: string
  committed: boolean
  totalRows: number
  validRows: number
  invalidRows: number
  imported: number
  headerErrors: string[]
  rows: QuestionImportRow[]
}

export interface BankSummary {
  id: number
  name: string
  description?: string
  course?: CourseRef
  questionCount: number
  createdAt: string
}

export interface BankDetail {
  bank: BankSummary
  questions: QuestionAdmin[]
}

export type AssignmentType = 'GROUP' | 'INDIVIDUAL' | 'CODE' | 'OPEN'
export type ReleaseMode = 'IMMEDIATE' | 'AFTER_DEADLINE' | 'MANUAL'
export type SessionState = 'WAITING' | 'LIVE' | 'ENDED'
export type AssignmentStatus = 'ACTIVE' | 'CLOSED'
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'AUTO_SUBMITTED'

export interface ProctoringSettings {
  enabled: boolean
  warningThreshold: number
  showWarnings: boolean
  flagForReview: boolean
  requireFullscreen: boolean
}

export interface AssignmentSummary {
  id: number
  name: string
  quizId: number
  quizTitle: string
  course?: CourseRef
  type: AssignmentType
  joinCode?: string
  availableFrom?: string
  deadline?: string
  durationMinutes: number
  maxAttempts: number
  status: AssignmentStatus
  liveSession: boolean
  sessionState?: SessionState
  resultsReleaseMode: ReleaseMode
  resultsVisible: boolean
  proctoringEnabled: boolean
  createdAt: string
}

export interface GroupRef {
  id: number
  name: string
  groupType: GroupType
  membershipMode: MembershipMode
}

export interface ProgressCounts {
  eligible: number
  notStarted: number
  inProgress: number
  submitted: number
  autoSubmitted: number
  connected: number
}

export interface AssignmentDetail {
  summary: AssignmentSummary
  codeOpenAccess: boolean
  leaderboardEnabled: boolean
  resultsReleased: boolean
  resultsReleasedAt?: string
  sessionStartedAt?: string
  sessionEndsAt?: string
  sessionEndedAt?: string
  proctoring: ProctoringSettings
  groups: GroupRef[]
  students: StudentRef[]
  progress: ProgressCounts
  questionCount: number
  totalMarks: number
  hasAttempts: boolean
  serverTime: string
}

export interface ResultRow {
  studentId: number
  registerNumber: string
  fullName: string
  academicYear: number
  department: string
  section?: string
  attemptId?: number
  attemptsUsed: number
  status: 'NOT_SUBMITTED' | AttemptStatus
  score?: number
  maximumScore?: number
  correctCount?: number
  questionCount?: number
  startedAt?: string
  submittedAt?: string
  warningCount: number
  flagged: boolean
}

export interface ResultsResponse {
  assignment: AssignmentSummary
  resultsReleased: boolean
  stats: {
    eligible: number
    submitted: number
    autoSubmitted: number
    inProgress: number
    notStarted: number
    average?: number
    highest?: number
    lowest?: number
    maximumScore: number
  }
  rows: ResultRow[]
}

export interface ReviewedOption {
  id: number
  text: string
  correct: boolean
}

export interface ReviewedQuestion {
  questionId: number
  number: number
  text: string
  points: number
  options: ReviewedOption[]
  selectedOptionId?: number
  correct?: boolean
  marksAwarded: number
  explanation?: string
  answeredAt?: string
}

export interface AdminAttemptDetail {
  attemptId: number
  student: StudentRef
  assignmentName: string
  quizTitle: string
  status: AttemptStatus
  attemptNumber: number
  startedAt: string
  endsAt: string
  submittedAt?: string
  score?: number
  maximumScore: number
  correctCount?: number
  questionCount: number
  warningCount: number
  flagged: boolean
  questions: ReviewedQuestion[]
}

export type ReviewStatus = 'PENDING' | 'REVIEWED' | 'FOLLOW_UP_REQUIRED'
export type ProctoringEventType = 'FOCUS_LOST' | 'FOCUS_RETURNED' | 'FULLSCREEN_EXIT' | 'FULLSCREEN_ENTER'

export interface ProctoringEventView {
  id: number
  eventType: ProctoringEventType
  occurredAt: string
  clientOccurredAt?: string
  metadata?: Record<string, unknown>
  warningCount: number
  reviewStatus: ReviewStatus
  reviewedBy?: string
  reviewedAt?: string
  reviewNotes?: string
}

export interface AttemptEvents {
  attemptId: number
  studentId: number
  registerNumber: string
  fullName: string
  attemptStatus: AttemptStatus
  warningCount: number
  flagged: boolean
  pendingReviewCount: number
  events: ProctoringEventView[]
}

export interface AssignmentEvents {
  assignmentId: number
  assignmentName: string
  warningThreshold: number
  attempts: AttemptEvents[]
}

export interface AdminDashboard {
  totalStudents: number
  activeStudents: number
  courses: number
  groups: number
  draftQuizzes: number
  publishedQuizzes: number
  activeAssignments: number
  liveSessions: number
  pendingProctoringReviews: number
  recentSubmissions: {
    attemptId: number
    assignmentId: number
    assignmentName: string
    quizTitle: string
    registerNumber: string
    fullName: string
    status: AttemptStatus
    score?: number
    maximumScore: number
    submittedAt: string
  }[]
}

// ------------------------------------------------------------------ student side

export type DashboardStatus =
  | 'UPCOMING'
  | 'AVAILABLE'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'AUTO_SUBMITTED'
  | 'EXPIRED'
  | 'RESULTS_RELEASED'

export interface StudentProctoring {
  enabled: boolean
  warningThreshold: number
  showWarnings: boolean
  requireFullscreen: boolean
  /** Warning events after which the attempt is submitted automatically. */
  autoSubmitWarnings: number
}

export interface StudentAssignment {
  id: number
  name: string
  quizTitle: string
  description?: string
  instructions?: string
  course?: CourseRef
  type: AssignmentType
  availableFrom?: string
  deadline?: string
  durationMinutes: number
  maxAttempts: number
  attemptsUsed: number
  status: DashboardStatus
  canStart: boolean
  blockedReason?: string
  liveSession: boolean
  sessionState?: SessionState
  inProgressAttemptId?: number
  latestFinalAttemptId?: number
  resultsVisible: boolean
  questionCount: number
  totalMarks: number
  proctoring: StudentProctoring
  leaderboardEnabled: boolean
  serverTime: string
}

export interface StudentQuestion {
  id: number
  number: number
  text: string
  points: number
  options: { id: number; text: string }[]
}

export interface SavedAnswer {
  questionId: number
  optionId?: number | null
  seq: number
  updatedAt: string
}

export interface AttemptView {
  attemptId: number
  assignmentId: number
  assignmentName: string
  quizTitle: string
  instructions?: string
  student: StudentRef
  status: AttemptStatus
  attemptNumber: number
  startedAt: string
  endsAt: string
  serverTime: string
  questions: StudentQuestion[]
  answers: SavedAnswer[]
  proctoring: StudentProctoring
  warningCount: number
}

export interface SaveAnswersResponse {
  savedAt: string
  answers: SavedAnswer[]
  endsAt: string
  serverTime: string
}

export interface SubmitResponse {
  attemptId: number
  status: AttemptStatus
  submittedAt: string
  resultsVisible: boolean
  answeredCount: number
  questionCount: number
}

export interface ResultView {
  attemptId: number
  assignmentId: number
  assignmentName: string
  quizTitle: string
  status: AttemptStatus
  attemptNumber: number
  submittedAt: string
  released: boolean
  score?: number
  maximumScore?: number
  correctCount?: number
  questionCount: number
  percentage?: number
  leaderboardEnabled: boolean
  questions: ReviewedQuestion[]
}

export interface LeaderboardEntry {
  rank: number
  name: string
  score: number
  maximumScore: number
  submittedAt: string
  me: boolean
}

export interface ProctoringEventResponse {
  warningCount: number
  warningThreshold: number
  showWarning: boolean
  flagged: boolean
  autoSubmitted: boolean
  message?: string
}
