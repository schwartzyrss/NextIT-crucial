-- NextIT Supabase PostgreSQL Schema
-- Run this script in your Supabase SQL Editor (https://supabase.com/dashboard/project/_/sql)

CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','INSTRUCTOR','EMPLOYER','ADMIN')),
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED' CHECK (verification_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED')),
    rejection_reason TEXT NULL,
    verified_at TIMESTAMPTZ NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS students (
    student_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    student_number VARCHAR(30) NOT NULL UNIQUE,
    program VARCHAR(100) NOT NULL,
    year_level INT NOT NULL,
    section VARCHAR(30) NULL,
    enrollment_status VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS instructors (
    instructor_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    employee_number VARCHAR(30) UNIQUE,
    department VARCHAR(100) NOT NULL,
    specialization VARCHAR(150) NULL
);

CREATE TABLE IF NOT EXISTS employers (
    employer_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    company_name VARCHAR(150) NOT NULL,
    industry VARCHAR(100) NULL,
    contact_person VARCHAR(100) NULL,
    contact_number VARCHAR(30) NULL,
    address VARCHAR(255) NULL,
    job_title VARCHAR(100) NULL
);

-- Profile views for compatibility with profile-based lookup queries
CREATE OR REPLACE VIEW student_profiles AS 
    SELECT s.*, u.email, u.username, u.first_name, u.last_name 
    FROM students s JOIN users u ON u.user_id = s.user_id;

CREATE OR REPLACE VIEW instructor_profiles AS 
    SELECT i.*, u.email, u.username, u.first_name, u.last_name 
    FROM instructors i JOIN users u ON u.user_id = i.user_id;

CREATE OR REPLACE VIEW employer_profiles AS 
    SELECT e.*, u.email, u.username, u.first_name, u.last_name 
    FROM employers e JOIN users u ON u.user_id = e.user_id;

CREATE TABLE IF NOT EXISTS subjects (
    subject_id SERIAL PRIMARY KEY,
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_name VARCHAR(150) NOT NULL,
    units DECIMAL(3,1) NOT NULL,
    description TEXT NULL,
    section VARCHAR(30) NULL,
    semester VARCHAR(20) NULL,
    academic_year VARCHAR(20) NULL,
    instructor_id INT NULL REFERENCES instructors(instructor_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    subject_id INT NOT NULL REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    academic_year VARCHAR(20) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    instructor_id INT NULL REFERENCES instructors(instructor_id) ON DELETE SET NULL,
    grade VARCHAR(20) NULL,
    enrolled_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_enrollment_subject UNIQUE (student_id, subject_id, academic_year, semester)
);
CREATE INDEX IF NOT EXISTS idx_enrollments_student ON enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_subject ON enrollments(subject_id);

ALTER TABLE subjects ADD COLUMN IF NOT EXISTS section VARCHAR(30) NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS semester VARCHAR(20) NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS academic_year VARCHAR(20) NULL;
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS instructor_id INT NULL REFERENCES instructors(instructor_id) ON DELETE SET NULL;

ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS instructor_id INT NULL REFERENCES instructors(instructor_id) ON DELETE SET NULL;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS grade VARCHAR(20) NULL;
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS enrolled_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

CREATE OR REPLACE VIEW student_enrollments AS
SELECT 
    e.enrollment_id,
    e.student_id,
    st.student_number,
    e.subject_id,
    COALESCE(e.instructor_id, s.instructor_id) AS instructor_id,
    e.grade,
    e.status,
    e.enrolled_at,
    e.academic_year,
    e.semester,
    s.subject_code,
    s.subject_name,
    s.subject_name AS subject_title,
    s.units,
    s.section
FROM enrollments e
JOIN students st ON st.student_id = e.student_id
JOIN subjects s ON s.subject_id = e.subject_id;

CREATE TABLE IF NOT EXISTS grades (
    grade_id SERIAL PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES enrollments(enrollment_id) ON DELETE CASCADE,
    instructor_id INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE RESTRICT,
    activity_name VARCHAR(150) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    score DECIMAL(6,2) NOT NULL,
    max_score DECIMAL(6,2) NOT NULL,
    grade DECIMAL(5,2) NULL,
    remarks TEXT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_grades_enrollment ON grades(enrollment_id);

CREATE TABLE IF NOT EXISTS attendance (
    attendance_id SERIAL PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES enrollments(enrollment_id) ON DELETE CASCADE,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    remarks TEXT NULL,
    recorded_by INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_attendance_enrollment ON attendance(enrollment_id);

CREATE TABLE IF NOT EXISTS technical_skills (
    skill_id SERIAL PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    description TEXT NULL
);

CREATE TABLE IF NOT EXISTS student_skills (
    student_skill_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    skill_id INT NOT NULL REFERENCES technical_skills(skill_id) ON DELETE RESTRICT,
    proficiency_level VARCHAR(30) NOT NULL,
    assessment_score DECIMAL(5,2) NULL,
    assessed_by INT NULL REFERENCES instructors(instructor_id) ON DELETE SET NULL,
    assessed_at TIMESTAMPTZ NULL,
    remarks TEXT NULL,
    CONSTRAINT uq_student_skill UNIQUE (student_id, skill_id)
);
CREATE INDEX IF NOT EXISTS idx_student_skills_student ON student_skills(student_id);

CREATE TABLE IF NOT EXISTS projects (
    project_id SERIAL PRIMARY KEY,
    student_id INT NULL REFERENCES students(student_id) ON DELETE SET NULL,
    subject_id INT NULL REFERENCES subjects(subject_id) ON DELETE SET NULL,
    project_title VARCHAR(200) NOT NULL,
    project_type VARCHAR(50) NOT NULL,
    description TEXT NULL,
    technologies_used TEXT NULL,
    repository_url VARCHAR(255) NULL,
    demo_url VARCHAR(255) NULL,
    project_date DATE NULL,
    status VARCHAR(30) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_projects_student ON projects(student_id);

CREATE TABLE IF NOT EXISTS project_members (
    project_member_id SERIAL PRIMARY KEY,
    project_id INT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    role VARCHAR(100) NULL,
    contribution TEXT NULL,
    contribution_notes TEXT NULL,
    CONSTRAINT uq_project_member UNIQUE (project_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_project_members_project ON project_members(project_id);
ALTER TABLE project_members ADD COLUMN IF NOT EXISTS contribution_notes TEXT NULL;

CREATE TABLE IF NOT EXISTS project_evaluations (
    evaluation_id SERIAL PRIMARY KEY,
    project_id INT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    instructor_id INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE RESTRICT,
    contribution_score DECIMAL(5,2) NULL,
    technical_score DECIMAL(5,2) NULL,
    teamwork_score DECIMAL(5,2) NULL,
    overall_score DECIMAL(5,2) NULL,
    feedback TEXT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_project_evaluation UNIQUE (project_id, student_id)
);

CREATE TABLE IF NOT EXISTS achievements (
    achievement_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    organization VARCHAR(150) NULL,
    achievement_date DATE NULL,
    verification_url VARCHAR(255) NULL
);
CREATE INDEX IF NOT EXISTS idx_achievements_student ON achievements(student_id);

CREATE TABLE IF NOT EXISTS certifications (
    certification_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    certification_name VARCHAR(200) NOT NULL,
    issuing_organization VARCHAR(150) NOT NULL,
    issue_date DATE NULL,
    expiration_date DATE NULL,
    credential_url VARCHAR(255) NULL
);
CREATE INDEX IF NOT EXISTS idx_certifications_student ON certifications(student_id);

CREATE TABLE IF NOT EXISTS portfolios (
    portfolio_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL UNIQUE REFERENCES students(student_id) ON DELETE CASCADE,
    headline VARCHAR(200) NULL,
    bio TEXT NULL,
    career_goal TEXT NULL,
    visibility VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS portfolio_access (
    access_id SERIAL PRIMARY KEY,
    portfolio_id INT NOT NULL REFERENCES portfolios(portfolio_id) ON DELETE CASCADE,
    employer_id INT NOT NULL REFERENCES employers(employer_id) ON DELETE CASCADE,
    access_status VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_portfolio_employer UNIQUE (portfolio_id, employer_id)
);
CREATE INDEX IF NOT EXISTS idx_portfolio_access_portfolio ON portfolio_access(portfolio_id);

CREATE TABLE IF NOT EXISTS academic_support (
    support_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    instructor_id INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE RESTRICT,
    subject_id INT NULL REFERENCES subjects(subject_id) ON DELETE SET NULL,
    support_type VARCHAR(50) NOT NULL,
    support_date DATE NOT NULL,
    reason TEXT NULL,
    action_taken TEXT NULL,
    outcome TEXT NULL,
    remarks TEXT NULL
);
CREATE INDEX IF NOT EXISTS idx_academic_support_student ON academic_support(student_id);

CREATE TABLE IF NOT EXISTS job_opportunities (
    opportunity_id SERIAL PRIMARY KEY,
    employer_id INT NOT NULL REFERENCES employers(employer_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    opportunity_type VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    required_skills TEXT NULL,
    location VARCHAR(150) NULL,
    deadline DATE NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_opportunities_employer ON job_opportunities(employer_id);

CREATE TABLE IF NOT EXISTS applications (
    application_id SERIAL PRIMARY KEY,
    opportunity_id INT NOT NULL REFERENCES job_opportunities(opportunity_id) ON DELETE CASCADE,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    application_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(30) NOT NULL,
    remarks TEXT NULL,
    reviewed_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_application UNIQUE (opportunity_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_applications_opportunity ON applications(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_applications_student ON applications(student_id);

CREATE TABLE IF NOT EXISTS institutional_records (
    record_id SERIAL PRIMARY KEY,
    record_type VARCHAR(20) NOT NULL CHECK (record_type IN ('STUDENT','INSTRUCTOR')),
    identifier_number VARCHAR(50) NOT NULL UNIQUE,
    institutional_email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    department_or_program VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS verification_requests (
    request_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','INSTRUCTOR','EMPLOYER','ADMIN')),
    id_number VARCHAR(50) NULL,
    institutional_email VARCHAR(100) NULL,
    organization_name VARCHAR(150) NULL,
    business_registration_number VARCHAR(100) NULL,
    document_reference VARCHAR(255) NULL,
    submission_notes TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED')),
    risk_score INT NOT NULL DEFAULT 0,
    risk_flags TEXT NULL,
    reviewer_id INT NULL REFERENCES users(user_id) ON DELETE SET NULL,
    reviewer_notes TEXT NULL,
    reviewed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_verification_requests_user ON verification_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_requests_status ON verification_requests(status);

CREATE TABLE IF NOT EXISTS security_audit_logs (
    log_id SERIAL PRIMARY KEY,
    user_id INT NULL REFERENCES users(user_id) ON DELETE SET NULL,
    actor_username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_user_id INT NULL,
    ip_address VARCHAR(50) NULL,
    details TEXT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_action ON security_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_created ON security_audit_logs(created_at);

CREATE TABLE IF NOT EXISTS recommendation_letters (
    letter_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    instructor_id INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE CASCADE,
    opportunity_id INT NULL REFERENCES job_opportunities(opportunity_id) ON DELETE SET NULL,
    application_id INT NULL REFERENCES applications(application_id) ON DELETE SET NULL,
    letter_title VARCHAR(200) NOT NULL,
    recommendation_body TEXT NOT NULL,
    endorsement_highlights TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'ISSUED', 'ATTACHED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    issued_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_rec_student ON recommendation_letters(student_id);
CREATE INDEX IF NOT EXISTS idx_rec_instructor ON recommendation_letters(instructor_id);
CREATE INDEX IF NOT EXISTS idx_rec_opportunity ON recommendation_letters(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_rec_application ON recommendation_letters(application_id);
CREATE INDEX IF NOT EXISTS idx_rec_status ON recommendation_letters(status);

-- ==============================================================================
-- SECURITY DEFINER RPC: lookup_account_for_recovery
-- Safely looks up user for password recovery across username, email, and
-- institutional/work emails (even when anonymous/unauthenticated and RLS is enabled).
-- Returns minimal account info needed for OTP dispatch without exposing password hash.
-- ==============================================================================
-- ==============================================================================
-- SECURITY DEFINER RPC: lookup_account_for_recovery
-- Safely looks up user for password recovery across username, email, and
-- institutional/work emails (even when anonymous/unauthenticated and RLS is enabled).
-- Returns minimal account info needed for OTP dispatch without exposing password hash.
-- ==============================================================================
CREATE OR REPLACE FUNCTION lookup_account_for_recovery(identifier TEXT)
RETURNS TABLE (
    user_id INT,
    username VARCHAR,
    email VARCHAR,
    role VARCHAR,
    first_name VARCHAR,
    last_name VARCHAR,
    masked_email TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    clean_id TEXT := LOWER(TRIM(identifier));
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        u.user_id,
        u.username::VARCHAR,
        COALESCE(
            NULLIF(TRIM(u.email), ''),
            NULLIF(TRIM(vr.institutional_email), ''),
            NULLIF(TRIM(ir.institutional_email), '')
        )::VARCHAR AS email,
        u.role::VARCHAR,
        u.first_name::VARCHAR,
        u.last_name::VARCHAR,
        CONCAT(
            SUBSTRING(
                COALESCE(
                    NULLIF(TRIM(u.email), ''),
                    NULLIF(TRIM(vr.institutional_email), ''),
                    NULLIF(TRIM(ir.institutional_email), '')
                ), 1, 2
            ),
            '***',
            SUBSTRING(
                COALESCE(
                    NULLIF(TRIM(u.email), ''),
                    NULLIF(TRIM(vr.institutional_email), ''),
                    NULLIF(TRIM(ir.institutional_email), '')
                )
                FROM POSITION('@' IN COALESCE(
                    NULLIF(TRIM(u.email), ''),
                    NULLIF(TRIM(vr.institutional_email), ''),
                    NULLIF(TRIM(ir.institutional_email), '')
                ))
            )
        )::TEXT AS masked_email
    FROM users u
    LEFT JOIN verification_requests vr ON vr.user_id = u.user_id
    LEFT JOIN students s ON s.user_id = u.user_id
    LEFT JOIN instructors i ON i.user_id = u.user_id
    LEFT JOIN employers emp ON emp.user_id = u.user_id
    LEFT JOIN institutional_records ir ON (
        (s.student_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(s.student_number))) OR
        (i.employee_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(i.employee_number))) OR
        (vr.id_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(vr.id_number))) OR
        (u.username IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(u.username))) OR
        (u.email IS NOT NULL AND LOWER(TRIM(ir.institutional_email)) = LOWER(TRIM(u.email))) OR
        (vr.institutional_email IS NOT NULL AND LOWER(TRIM(ir.institutional_email)) = LOWER(TRIM(vr.institutional_email)))
    )
    WHERE 
        LOWER(TRIM(u.username)) = clean_id
        OR LOWER(TRIM(u.email)) = clean_id
        OR (vr.institutional_email IS NOT NULL AND LOWER(TRIM(vr.institutional_email)) = clean_id)
        OR (ir.institutional_email IS NOT NULL AND LOWER(TRIM(ir.institutional_email)) = clean_id)
        OR (ir.identifier_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = clean_id)
        OR (s.student_number IS NOT NULL AND LOWER(TRIM(s.student_number)) = clean_id)
        OR (i.employee_number IS NOT NULL AND LOWER(TRIM(i.employee_number)) = clean_id)
        OR (vr.id_number IS NOT NULL AND LOWER(TRIM(vr.id_number)) = clean_id)
    LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION lookup_account_for_recovery(TEXT) TO anon, authenticated, service_role, public;

-- ------------------------------------------------------------------------------
-- AUTOMATED STUDENT ID GENERATION FUNCTION (RPC) & TRIGGER
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION generate_next_student_number(p_year INT DEFAULT EXTRACT(YEAR FROM CURRENT_DATE)::INT)
RETURNS TEXT AS $$
DECLARE
    v_prefix TEXT;
    v_max_seq INT := 0;
    v_num TEXT;
    v_seq INT;
BEGIN
    v_prefix := p_year || '-';
    FOR v_num IN 
        SELECT student_number FROM students WHERE student_number LIKE v_prefix || '%'
        UNION
        SELECT id_number FROM verification_requests WHERE id_number LIKE v_prefix || '%'
    LOOP
        BEGIN
            v_seq := SUBSTRING(v_num FROM LENGTH(v_prefix) + 1)::INT;
            IF v_seq > v_max_seq THEN
                v_max_seq := v_seq;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            -- Ignore non-numeric suffixes
        END;
    END LOOP;
    RETURN v_prefix || LPAD((v_max_seq + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;

GRANT EXECUTE ON FUNCTION generate_next_student_number(INT) TO anon, authenticated, service_role, public;

CREATE OR REPLACE FUNCTION trg_auto_assign_student_number()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.student_number IS NULL OR TRIM(NEW.student_number) = '' THEN
        NEW.student_number := generate_next_student_number(EXTRACT(YEAR FROM CURRENT_DATE)::INT);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_students_auto_number ON students;
CREATE TRIGGER trg_students_auto_number
BEFORE INSERT ON students
FOR EACH ROW
EXECUTE FUNCTION trg_auto_assign_student_number();



