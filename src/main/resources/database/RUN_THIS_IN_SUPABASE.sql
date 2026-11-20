-- ==============================================================================
-- NextIT: Full Database Setup Script for Supabase (PostgreSQL)
-- ==============================================================================
-- INSTRUCTIONS:
-- 1. Open your Supabase Dashboard:
--    https://supabase.com/dashboard/project/qlmrcmaqkvtmciursqvr/sql/new
-- 2. Select everything in this file (Ctrl+A, Ctrl+C)
-- 3. Paste into the Supabase SQL Editor and click the green "Run" button.
-- 4. Go to the "Table Editor" tab to verify all 20 tables and initial records!
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. DROP EXISTING TABLES (Clean Slate in Dependency Order)
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS security_audit_logs CASCADE;
DROP TABLE IF EXISTS verification_requests CASCADE;
DROP TABLE IF EXISTS institutional_records CASCADE;
DROP TABLE IF EXISTS applications CASCADE;
DROP TABLE IF EXISTS job_opportunities CASCADE;
DROP TABLE IF EXISTS academic_support CASCADE;
DROP TABLE IF EXISTS portfolio_access CASCADE;
DROP TABLE IF EXISTS portfolios CASCADE;
DROP TABLE IF EXISTS certifications CASCADE;
DROP TABLE IF EXISTS achievements CASCADE;
DROP TABLE IF EXISTS project_evaluations CASCADE;
DROP TABLE IF EXISTS project_members CASCADE;
DROP TABLE IF EXISTS projects CASCADE;
DROP TABLE IF EXISTS student_skills CASCADE;
DROP TABLE IF EXISTS technical_skills CASCADE;
DROP TABLE IF EXISTS attendance CASCADE;
DROP TABLE IF EXISTS grades CASCADE;
DROP TABLE IF EXISTS enrollments CASCADE;
DROP TABLE IF EXISTS subjects CASCADE;
DROP TABLE IF EXISTS employers CASCADE;
DROP TABLE IF EXISTS instructors CASCADE;
DROP TABLE IF EXISTS students CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ------------------------------------------------------------------------------
-- 2. TABLE DEFINITIONS (23 TABLES)
-- ------------------------------------------------------------------------------

-- (1) Users
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','INSTRUCTOR','EMPLOYER')),
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED')),
    rejection_reason TEXT NULL,
    verified_at TIMESTAMPTZ NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- (2) Students
CREATE TABLE students (
    student_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    student_number VARCHAR(30) NOT NULL UNIQUE,
    program VARCHAR(100) NOT NULL,
    year_level INT NOT NULL,
    section VARCHAR(30) NULL,
    enrollment_status VARCHAR(30) NOT NULL
);

-- (3) Instructors
CREATE TABLE instructors (
    instructor_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    employee_number VARCHAR(30) UNIQUE,
    department VARCHAR(100) NOT NULL,
    specialization VARCHAR(150) NULL
);

-- (4) Employers
CREATE TABLE employers (
    employer_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    company_name VARCHAR(150) NOT NULL,
    industry VARCHAR(100) NULL,
    contact_person VARCHAR(100) NULL,
    contact_number VARCHAR(30) NULL,
    address VARCHAR(255) NULL
);

-- (5) Subjects
CREATE TABLE subjects (
    subject_id SERIAL PRIMARY KEY,
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_name VARCHAR(150) NOT NULL,
    units DECIMAL(3,1) NOT NULL,
    description TEXT NULL
);

-- (6) Enrollments
CREATE TABLE enrollments (
    enrollment_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    subject_id INT NOT NULL REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    academic_year VARCHAR(20) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL
);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_subject ON enrollments(subject_id);

-- (7) Grades
CREATE TABLE grades (
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
CREATE INDEX idx_grades_enrollment ON grades(enrollment_id);

-- (8) Attendance
CREATE TABLE attendance (
    attendance_id SERIAL PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES enrollments(enrollment_id) ON DELETE CASCADE,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    remarks TEXT NULL,
    recorded_by INT NOT NULL REFERENCES instructors(instructor_id) ON DELETE RESTRICT
);
CREATE INDEX idx_attendance_enrollment ON attendance(enrollment_id);

-- (9) Technical Skills
CREATE TABLE technical_skills (
    skill_id SERIAL PRIMARY KEY,
    skill_name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    description TEXT NULL
);

-- (10) Student Skills
CREATE TABLE student_skills (
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
CREATE INDEX idx_student_skills_student ON student_skills(student_id);

-- (11) Projects
CREATE TABLE projects (
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
CREATE INDEX idx_projects_student ON projects(student_id);

-- (12) Project Members
CREATE TABLE project_members (
    project_member_id SERIAL PRIMARY KEY,
    project_id INT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    role VARCHAR(100) NULL,
    contribution TEXT NULL,
    contribution_notes TEXT NULL,
    CONSTRAINT uq_project_member UNIQUE (project_id, student_id)
);
CREATE INDEX idx_project_members_project ON project_members(project_id);
ALTER TABLE project_members ADD COLUMN IF NOT EXISTS contribution_notes TEXT NULL;

-- (13) Project Evaluations
CREATE TABLE project_evaluations (
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

-- (14) Achievements
CREATE TABLE achievements (
    achievement_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    organization VARCHAR(150) NULL,
    achievement_date DATE NULL,
    verification_url VARCHAR(255) NULL
);
CREATE INDEX idx_achievements_student ON achievements(student_id);

-- (15) Certifications
CREATE TABLE certifications (
    certification_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    certification_name VARCHAR(200) NOT NULL,
    issuing_organization VARCHAR(150) NOT NULL,
    issue_date DATE NULL,
    expiration_date DATE NULL,
    credential_url VARCHAR(255) NULL
);
CREATE INDEX idx_certifications_student ON certifications(student_id);

-- (16) Portfolios
CREATE TABLE portfolios (
    portfolio_id SERIAL PRIMARY KEY,
    student_id INT NOT NULL UNIQUE REFERENCES students(student_id) ON DELETE CASCADE,
    headline VARCHAR(200) NULL,
    bio TEXT NULL,
    career_goal TEXT NULL,
    visibility VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- (17) Portfolio Access
CREATE TABLE portfolio_access (
    access_id SERIAL PRIMARY KEY,
    portfolio_id INT NOT NULL REFERENCES portfolios(portfolio_id) ON DELETE CASCADE,
    employer_id INT NOT NULL REFERENCES employers(employer_id) ON DELETE CASCADE,
    access_status VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_portfolio_employer UNIQUE (portfolio_id, employer_id)
);
CREATE INDEX idx_portfolio_access_portfolio ON portfolio_access(portfolio_id);

-- (18) Academic Support
CREATE TABLE academic_support (
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
CREATE INDEX idx_academic_support_student ON academic_support(student_id);

-- (19) Job Opportunities
CREATE TABLE job_opportunities (
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
CREATE INDEX idx_opportunities_employer ON job_opportunities(employer_id);

-- (20) Applications
CREATE TABLE applications (
    application_id SERIAL PRIMARY KEY,
    opportunity_id INT NOT NULL REFERENCES job_opportunities(opportunity_id) ON DELETE CASCADE,
    student_id INT NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
    application_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(30) NOT NULL,
    remarks TEXT NULL,
    reviewed_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_application UNIQUE (opportunity_id, student_id)
);
CREATE INDEX idx_applications_opportunity ON applications(opportunity_id);
CREATE INDEX idx_applications_student ON applications(student_id);

-- (21) Institutional Records (Registrar Pre-Enrolled Records)
CREATE TABLE institutional_records (
    record_id SERIAL PRIMARY KEY,
    record_type VARCHAR(20) NOT NULL CHECK (record_type IN ('STUDENT','INSTRUCTOR')),
    identifier_number VARCHAR(50) NOT NULL UNIQUE,
    institutional_email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    department_or_program VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- (22) Verification Requests
CREATE TABLE verification_requests (
    request_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','INSTRUCTOR','EMPLOYER')),
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
CREATE INDEX idx_verification_requests_user ON verification_requests(user_id);
CREATE INDEX idx_verification_requests_status ON verification_requests(status);

-- (23) Security Audit Logs
CREATE TABLE security_audit_logs (
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
CREATE INDEX idx_security_audit_logs_action ON security_audit_logs(action);
CREATE INDEX idx_security_audit_logs_created ON security_audit_logs(created_at);

CREATE TABLE recommendation_letters (
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
CREATE INDEX idx_rec_student ON recommendation_letters(student_id);
CREATE INDEX idx_rec_instructor ON recommendation_letters(instructor_id);
CREATE INDEX idx_rec_opportunity ON recommendation_letters(opportunity_id);
CREATE INDEX idx_rec_application ON recommendation_letters(application_id);
CREATE INDEX idx_rec_status ON recommendation_letters(status);

-- ------------------------------------------------------------------------------
-- 3. SEED INITIAL DEMO DATA
-- ------------------------------------------------------------------------------
DO $$
DECLARE
    v_student_user    INT;
    v_student2_user   INT;
    v_instructor_user INT;
    v_employer_user   INT;
    v_admin_user      INT;
    v_admin_kakong    INT;

    v_student_id      INT;
    v_student2_id     INT;
    v_instructor_id   INT;
    v_employer_id     INT;

    v_sub_301         INT;
    v_sub_302         INT;
    v_sub_303         INT;

    v_enr_301         INT;
    v_enr_302         INT;

    v_skill_java      INT;
    v_skill_mysql     INT;
    v_skill_cyber     INT;

    v_proj_id         INT;
    v_port_id         INT;
    v_opp_intern      INT;
BEGIN
    -- Password for all demo accounts is: password123
    -- (Bcrypt hash: $2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO)

    -- A. Users
    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('kakonggg',    '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', 'kakonggg@nextit.edu',   'INSTRUCTOR', 'Kakong', 'Instructor', 'VERIFIED', NOW(), TRUE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_admin_kakong;

    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('student1',    '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', 'student@nextit.edu',    'STUDENT',   'Alex',   'Reyes', 'VERIFIED', NOW(), FALSE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_student_user;

    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('student2',    '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', 'student2@nextit.edu',   'STUDENT',   'Bianca', 'Cruz', 'VERIFIED', NOW(), FALSE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_student2_user;

    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('instructor1', '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', 'instructor@nextit.edu', 'INSTRUCTOR','Maria',  'Santos', 'VERIFIED', NOW(), FALSE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_instructor_user;

    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('employer1',   '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', 'employer@techcorp.com', 'EMPLOYER',  'John',   'Dela Cruz', 'VERIFIED', NOW(), FALSE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_employer_user;

    -- B. Students
    INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status)
    VALUES (v_student_user, '2023-0001', 'BS Information Technology', 3, 'A', 'Enrolled')
    ON CONFLICT (student_number) DO UPDATE SET program = EXCLUDED.program
    RETURNING student_id INTO v_student_id;

    INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status)
    VALUES (v_student2_user, '2023-0002', 'BS Information Technology', 3, 'A', 'Enrolled')
    ON CONFLICT (student_number) DO UPDATE SET program = EXCLUDED.program
    RETURNING student_id INTO v_student2_id;

    -- C. Instructors
    INSERT INTO instructors (user_id, employee_number, department, specialization)
    VALUES (v_instructor_user, 'EMP-1001', 'Computer Science', 'Software Engineering')
    ON CONFLICT (user_id) DO UPDATE SET department = EXCLUDED.department
    RETURNING instructor_id INTO v_instructor_id;

    -- D. Employers
    INSERT INTO employers (user_id, company_name, industry, contact_person, contact_number, address)
    VALUES (v_employer_user, 'TechCorp Solutions', 'Software Development', 'John Dela Cruz', '09171234567', 'Makati City')
    ON CONFLICT (user_id) DO UPDATE SET company_name = EXCLUDED.company_name
    RETURNING employer_id INTO v_employer_id;

    -- E. Subjects
    INSERT INTO subjects (subject_code, subject_name, units, description)
    VALUES ('IT301', 'Object-Oriented Programming', 3.0, 'OOP concepts with Java')
    ON CONFLICT (subject_code) DO UPDATE SET subject_name = EXCLUDED.subject_name
    RETURNING subject_id INTO v_sub_301;

    INSERT INTO subjects (subject_code, subject_name, units, description)
    VALUES ('IT302', 'Database Management Systems', 3.0, 'Relational databases and SQL')
    ON CONFLICT (subject_code) DO UPDATE SET subject_name = EXCLUDED.subject_name
    RETURNING subject_id INTO v_sub_302;

    INSERT INTO subjects (subject_code, subject_name, units, description)
    VALUES ('IT303', 'Web Development', 3.0, 'Modern web application development')
    ON CONFLICT (subject_code) DO UPDATE SET subject_name = EXCLUDED.subject_name
    RETURNING subject_id INTO v_sub_303;

    -- F. Enrollments
    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_301, '2025-2026', '1st Semester', 'Enrolled')
    RETURNING enrollment_id INTO v_enr_301;

    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_302, '2025-2026', '1st Semester', 'Enrolled')
    RETURNING enrollment_id INTO v_enr_302;

    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_303, '2025-2026', '1st Semester', 'Enrolled');

    -- G. Grades
    INSERT INTO grades (enrollment_id, instructor_id, activity_name, activity_type, score, max_score, grade, remarks)
    VALUES
    (v_enr_301, v_instructor_id, 'Midterm Exam', 'Examination', 42.00, 50.00, 84.00, 'Good performance'),
    (v_enr_301, v_instructor_id, 'Quiz 1', 'Quiz', 18.00, 20.00, 90.00, 'Well done'),
    (v_enr_302, v_instructor_id, 'Lab 1', 'Laboratory', 45.00, 50.00, 90.00, 'Excellent queries');

    -- H. Attendance
    INSERT INTO attendance (enrollment_id, attendance_date, status, remarks, recorded_by)
    VALUES
    (v_enr_301, '2026-09-01', 'Present', NULL, v_instructor_id),
    (v_enr_301, '2026-09-08', 'Late', 'Arrived 10 minutes late', v_instructor_id),
    (v_enr_302, '2026-09-01', 'Present', NULL, v_instructor_id);

    -- I. Technical Skills
    INSERT INTO technical_skills (skill_name, category, description)
    VALUES ('Java Programming', 'Programming', 'Core Java development')
    ON CONFLICT (skill_name) DO UPDATE SET category = EXCLUDED.category
    RETURNING skill_id INTO v_skill_java;

    INSERT INTO technical_skills (skill_name, category, description)
    VALUES ('MySQL', 'Database Management', 'Relational database design and queries')
    ON CONFLICT (skill_name) DO UPDATE SET category = EXCLUDED.category
    RETURNING skill_id INTO v_skill_mysql;

    INSERT INTO technical_skills (skill_name, category, description)
    VALUES ('Cybersecurity Basics', 'Cybersecurity', 'Fundamental security concepts')
    ON CONFLICT (skill_name) DO UPDATE SET category = EXCLUDED.category
    RETURNING skill_id INTO v_skill_cyber;

    -- J. Student Skills
    INSERT INTO student_skills (student_id, skill_id, proficiency_level, assessment_score, assessed_by, assessed_at, remarks)
    VALUES
    (v_student_id, v_skill_java, 'Intermediate', 85.00, v_instructor_id, '2026-09-10 09:00:00+00', 'Solid OOP skills'),
    (v_student_id, v_skill_mysql, 'Beginner', 70.00, v_instructor_id, '2026-09-10 09:00:00+00', 'Needs practice with joins')
    ON CONFLICT (student_id, skill_id) DO NOTHING;

    -- K. Projects
    INSERT INTO projects (student_id, subject_id, project_title, project_type, description, technologies_used, repository_url, demo_url, project_date, status)
    VALUES (v_student_id, v_sub_301, 'NextIT Tracker', 'Capstone', 'Student progress tracking desktop app', 'Java, JavaFX, PostgreSQL', 'https://github.com/alex/nextit', NULL, '2026-09-15', 'Draft')
    RETURNING project_id INTO v_proj_id;

    -- L. Project Members
    INSERT INTO project_members (project_id, student_id, role, contribution)
    VALUES (v_proj_id, v_student2_id, 'Frontend Developer', 'Built the dashboard screens and interaction flows')
    ON CONFLICT (project_id, student_id) DO NOTHING;

    -- M. Project Evaluations
    INSERT INTO project_evaluations (project_id, student_id, instructor_id, contribution_score, technical_score, teamwork_score, overall_score, feedback)
    VALUES (v_proj_id, v_student_id, v_instructor_id, 90.00, 88.00, 85.00, 87.67, 'Strong implementation and clear documentation')
    ON CONFLICT (project_id, student_id) DO NOTHING;

    -- N. Achievements
    INSERT INTO achievements (student_id, title, description, organization, achievement_date, verification_url)
    VALUES (v_student_id, 'Dean''s Lister', 'Top of class for AY 2025-2026', 'State University', '2026-08-30', NULL);

    -- O. Certifications
    INSERT INTO certifications (student_id, certification_name, issuing_organization, issue_date, expiration_date, credential_url)
    VALUES (v_student_id, 'Oracle Certified Java Foundations', 'Oracle', '2026-03-01', '2029-03-01', 'https://certs.example.com/java-foundations');

    -- P. Portfolios
    INSERT INTO portfolios (student_id, headline, bio, career_goal, visibility)
    VALUES (v_student_id, 'Aspiring Software Engineer', 'IT student passionate about desktop and web systems.', 'Become a full-stack developer', 'Approved')
    ON CONFLICT (student_id) DO UPDATE SET visibility = EXCLUDED.visibility
    RETURNING portfolio_id INTO v_port_id;

    -- Q. Portfolio Access
    INSERT INTO portfolio_access (portfolio_id, employer_id, access_status, granted_at)
    VALUES (v_port_id, v_employer_id, 'Approved', '2026-09-05 10:00:00+00')
    ON CONFLICT (portfolio_id, employer_id) DO NOTHING;

    -- R. Job Opportunities
    INSERT INTO job_opportunities (employer_id, title, opportunity_type, description, required_skills, location, deadline, status)
    VALUES
    (v_employer_id, 'Software Development Intern', 'Internship', 'Assist in building internal tools.', 'Java, PostgreSQL, Git', 'Makati City', '2026-12-31', 'Open')
    RETURNING opportunity_id INTO v_opp_intern;

    INSERT INTO job_opportunities (employer_id, title, opportunity_type, description, required_skills, location, deadline, status)
    VALUES (v_employer_id, 'Junior Java Developer', 'Employment', 'Entry-level Java developer role.', 'Java, JavaFX, SQL', 'Remote', '2026-11-30', 'Open');

    -- S. Applications
    INSERT INTO applications (opportunity_id, student_id, status, remarks)
    VALUES (v_opp_intern, v_student_id, 'Pending', 'Interested in the internship program')
    ON CONFLICT (opportunity_id, student_id) DO NOTHING;

    -- T. Academic Support
    INSERT INTO academic_support (student_id, instructor_id, subject_id, support_type, support_date, reason, action_taken, outcome, remarks)
    VALUES (v_student_id, v_instructor_id, v_sub_302, 'Remedial', '2026-09-12', 'Difficulty with SQL joins', 'Extra exercises on joins', 'Improved', 'Follow up next week');

    -- U. Institutional Records (Registrar Database)
    INSERT INTO institutional_records (record_type, identifier_number, institutional_email, full_name, department_or_program, is_active) VALUES
    ('STUDENT', '2023-0001', 'student@nextit.edu', 'Alex Reyes', 'BS Information Technology', TRUE),
    ('STUDENT', '2023-0002', 'student2@nextit.edu', 'Bianca Cruz', 'BS Information Technology', TRUE),
    ('STUDENT', '2026-0042', 'carlos.mendoza@nextit.edu', 'Carlos Mendoza', 'BS Computer Science', TRUE),
    ('STUDENT', '2026-0099', 'diane.tan@dlsu.edu.ph', 'Diane Tan', 'BS Information Systems', TRUE),
    ('STUDENT', '25-16522', '25-16522@g.bastate-u.edu.ph', 'BatStateU Scholar', 'BS Information Technology', TRUE),
    ('INSTRUCTOR', 'EMP-1001', 'instructor@nextit.edu', 'Maria Santos', 'Computer Science', TRUE),
    ('INSTRUCTOR', 'EMP-1002', 'roberto.garcia@nextit.edu', 'Roberto Garcia', 'Information Technology', TRUE)
    ON CONFLICT (identifier_number) DO NOTHING;

    -- V. Verification Requests
    INSERT INTO verification_requests (user_id, role, id_number, institutional_email, organization_name, business_registration_number, document_reference, submission_notes, status, risk_score, risk_flags, reviewed_at) VALUES
    (v_student_user, 'STUDENT', '2023-0001', 'student@nextit.edu', 'NextIT Institute of Technology', NULL, 'Official Registrar Enrolment Card', 'Automated institutional registrar validation successful.', 'VERIFIED', 0, 'REGISTRAR_VERIFIED_EXACT_MATCH', NOW()),
    (v_student2_user, 'STUDENT', '2023-0002', 'student2@nextit.edu', 'NextIT Institute of Technology', NULL, 'Official Registrar Enrolment Card', 'Automated institutional registrar validation successful.', 'VERIFIED', 0, 'REGISTRAR_VERIFIED_EXACT_MATCH', NOW()),
    (v_instructor_user, 'INSTRUCTOR', 'EMP-1001', 'instructor@nextit.edu', 'College of Computer Studies', NULL, 'Faculty Appointment Form CS-2024', 'Faculty credentials confirmed against institutional records.', 'VERIFIED', 5, 'REGISTRAR_FACULTY_CONFIRMED', NOW()),
    (v_employer_user, 'EMPLOYER', NULL, 'employer@techcorp.com', 'TechCorp Solutions', 'SEC-CS2021008891-TIN009', 'SEC Certificate of Incorporation & DTI Permit', 'Corporate registration verified.', 'VERIFIED', 10, 'CORPORATE_DOMAIN_DETECTED', NOW());

    -- W. Security Audit Logs
    INSERT INTO security_audit_logs (user_id, actor_username, action, target_user_id, ip_address, details, severity) VALUES
    (v_instructor_user, 'instructor1', 'SYSTEM_INITIALIZED', NULL, '127.0.0.1', 'Security and identity verification system initialized.', 'INFO'),
    (v_student_user, 'student1', 'LOGIN_SUCCESS', v_student_user, '127.0.0.1', 'Successful authentication for verified student account.', 'INFO');

END $$;

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
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        u.user_id,
        u.username::VARCHAR,
        COALESCE(vr.institutional_email, ir.institutional_email, u.email)::VARCHAR AS email,
        u.role::VARCHAR,
        u.first_name::VARCHAR,
        u.last_name::VARCHAR,
        CONCAT(
            SUBSTRING(COALESCE(vr.institutional_email, ir.institutional_email, u.email), 1, 2),
            '***',
            SUBSTRING(COALESCE(vr.institutional_email, ir.institutional_email, u.email) FROM POSITION('@' IN COALESCE(vr.institutional_email, ir.institutional_email, u.email)))
        )::TEXT AS masked_email
    FROM users u
    LEFT JOIN verification_requests vr ON vr.user_id = u.user_id
    LEFT JOIN students s ON s.user_id = u.user_id
    LEFT JOIN instructors i ON i.user_id = u.user_id
    LEFT JOIN employers emp ON emp.user_id = u.user_id
    LEFT JOIN institutional_records ir ON (
        (s.student_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(s.student_number))) OR
        (i.employee_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(i.employee_number))) OR
        (vr.id_number IS NOT NULL AND LOWER(TRIM(ir.identifier_number)) = LOWER(TRIM(vr.id_number)))
    )
    WHERE 
        LOWER(TRIM(u.username)) = LOWER(TRIM(identifier))
        OR LOWER(TRIM(u.email)) = LOWER(TRIM(identifier))
        OR (vr.institutional_email IS NOT NULL AND LOWER(TRIM(vr.institutional_email)) = LOWER(TRIM(identifier)))
        OR (ir.institutional_email IS NOT NULL AND LOWER(TRIM(ir.institutional_email)) = LOWER(TRIM(identifier)))
        OR (s.student_number IS NOT NULL AND LOWER(TRIM(s.student_number)) = LOWER(TRIM(identifier)))
        OR (i.employee_number IS NOT NULL AND LOWER(TRIM(i.employee_number)) = LOWER(TRIM(identifier)))
        OR (vr.id_number IS NOT NULL AND LOWER(TRIM(vr.id_number)) = LOWER(TRIM(identifier)))
    LIMIT 1;
END;
$$;

GRANT EXECUTE ON FUNCTION lookup_account_for_recovery(TEXT) TO anon, authenticated, service_role;


