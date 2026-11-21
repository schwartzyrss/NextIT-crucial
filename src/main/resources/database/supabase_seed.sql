-- NextIT Supabase PostgreSQL Seed Data
-- Run this in your Supabase SQL Editor after running supabase_schema.sql.
-- Demo credentials for all accounts: username / password123

DO $$
DECLARE
    v_student_user    INT;
    v_student2_user   INT;
    v_batstate_user   INT;
    v_instructor_user INT;
    v_employer_user   INT;
    v_admin_user      INT;

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
    -- 1. Users
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

    -- Seed BatStateU Scholar student user
    INSERT INTO users (username, password_hash, email, role, first_name, last_name, verification_status, verified_at, mfa_enabled) VALUES
    ('25-16522',    '$2a$10$AwV9Ci7bWr2MEFJiXixMOuyTY/UvFPDJtbyjva7sFKCcN0iESR2GO', '25-16522@g.bastate-u.edu.ph', 'STUDENT', 'BatStateU', 'Scholar', 'VERIFIED', NOW(), FALSE)
    ON CONFLICT (username) DO UPDATE SET email = EXCLUDED.email, verification_status = 'VERIFIED'
    RETURNING user_id INTO v_batstate_user;

    -- 2. Students
    INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status)
    VALUES (v_student_user, '2023-0001', 'BS Information Technology', 3, 'A', 'Enrolled')
    ON CONFLICT (student_number) DO UPDATE SET program = EXCLUDED.program
    RETURNING student_id INTO v_student_id;

    INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status)
    VALUES (v_student2_user, '2023-0002', 'BS Information Technology', 3, 'A', 'Enrolled')
    ON CONFLICT (student_number) DO UPDATE SET program = EXCLUDED.program
    RETURNING student_id INTO v_student2_id;

    INSERT INTO students (user_id, student_number, program, year_level, section, enrollment_status)
    VALUES (v_batstate_user, '25-16522', 'BS Information Technology', 3, 'A', 'Enrolled')
    ON CONFLICT (student_number) DO UPDATE SET program = EXCLUDED.program;

    -- 3. Instructors
    INSERT INTO instructors (user_id, employee_number, department, specialization)
    VALUES (v_instructor_user, 'EMP-1001', 'Computer Science', 'Software Engineering')
    ON CONFLICT (user_id) DO UPDATE SET department = EXCLUDED.department
    RETURNING instructor_id INTO v_instructor_id;

    -- 4. Employers
    INSERT INTO employers (user_id, company_name, industry, contact_person, contact_number, address)
    VALUES (v_employer_user, 'TechCorp Solutions', 'Software Development', 'John Dela Cruz', '09171234567', 'Makati City')
    ON CONFLICT (user_id) DO UPDATE SET company_name = EXCLUDED.company_name
    RETURNING employer_id INTO v_employer_id;

    -- 5. Subjects
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

    -- 6. Enrollments
    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_301, '2025-2026', '1st Semester', 'Enrolled')
    RETURNING enrollment_id INTO v_enr_301;

    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_302, '2025-2026', '1st Semester', 'Enrolled')
    RETURNING enrollment_id INTO v_enr_302;

    INSERT INTO enrollments (student_id, subject_id, academic_year, semester, status)
    VALUES (v_student_id, v_sub_303, '2025-2026', '1st Semester', 'Enrolled');

    -- 7. Grades
    INSERT INTO grades (enrollment_id, instructor_id, activity_name, activity_type, score, max_score, grade, remarks)
    VALUES
    (v_enr_301, v_instructor_id, 'Midterm Exam', 'Examination', 42.00, 50.00, 84.00, 'Good performance'),
    (v_enr_301, v_instructor_id, 'Quiz 1', 'Quiz', 18.00, 20.00, 90.00, 'Well done'),
    (v_enr_302, v_instructor_id, 'Lab 1', 'Laboratory', 45.00, 50.00, 90.00, 'Excellent queries');

    -- 8. Attendance
    INSERT INTO attendance (enrollment_id, attendance_date, status, remarks, recorded_by)
    VALUES
    (v_enr_301, '2026-09-01', 'Present', NULL, v_instructor_id),
    (v_enr_301, '2026-09-08', 'Late', 'Arrived 10 minutes late', v_instructor_id),
    (v_enr_302, '2026-09-01', 'Present', NULL, v_instructor_id);

    -- 9. Technical Skills
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

    -- 10. Student Skills
    INSERT INTO student_skills (student_id, skill_id, proficiency_level, assessment_score, assessed_by, assessed_at, remarks)
    VALUES
    (v_student_id, v_skill_java, 'Intermediate', 85.00, v_instructor_id, '2026-09-10 09:00:00+00', 'Solid OOP skills'),
    (v_student_id, v_skill_mysql, 'Beginner', 70.00, v_instructor_id, '2026-09-10 09:00:00+00', 'Needs practice with joins')
    ON CONFLICT (student_id, skill_id) DO NOTHING;

    -- 11. Projects
    INSERT INTO projects (student_id, subject_id, project_title, project_type, description, technologies_used, repository_url, demo_url, project_date, status)
    VALUES (v_student_id, v_sub_301, 'NextIT Tracker', 'Capstone', 'Student progress tracking desktop app', 'Java, JavaFX, PostgreSQL', 'https://github.com/alex/nextit', NULL, '2026-09-15', 'Draft')
    RETURNING project_id INTO v_proj_id;

    -- 12. Project Members
    INSERT INTO project_members (project_id, student_id, role, contribution)
    VALUES (v_proj_id, v_student2_id, 'Frontend Developer', 'Built the dashboard screens and interaction flows')
    ON CONFLICT (project_id, student_id) DO NOTHING;

    -- 13. Project Evaluations
    INSERT INTO project_evaluations (project_id, student_id, instructor_id, contribution_score, technical_score, teamwork_score, overall_score, feedback)
    VALUES (v_proj_id, v_student_id, v_instructor_id, 90.00, 88.00, 85.00, 87.67, 'Strong implementation and clear documentation')
    ON CONFLICT (project_id, student_id) DO NOTHING;

    -- 14. Achievements
    INSERT INTO achievements (student_id, title, description, organization, achievement_date, verification_url)
    VALUES (v_student_id, 'Dean''s Lister', 'Top of class for AY 2025-2026', 'State University', '2026-08-30', NULL);

    -- 15. Certifications
    INSERT INTO certifications (student_id, certification_name, issuing_organization, issue_date, expiration_date, credential_url)
    VALUES (v_student_id, 'Oracle Certified Java Foundations', 'Oracle', '2026-03-01', '2029-03-01', 'https://certs.example.com/java-foundations');

    -- 16. Portfolios
    INSERT INTO portfolios (student_id, headline, bio, career_goal, visibility)
    VALUES (v_student_id, 'Aspiring Software Engineer', 'IT student passionate about desktop and web systems.', 'Become a full-stack developer', 'Approved')
    ON CONFLICT (student_id) DO UPDATE SET visibility = EXCLUDED.visibility
    RETURNING portfolio_id INTO v_port_id;

    -- 17. Portfolio Access
    INSERT INTO portfolio_access (portfolio_id, employer_id, access_status, granted_at)
    VALUES (v_port_id, v_employer_id, 'Approved', '2026-09-05 10:00:00+00')
    ON CONFLICT (portfolio_id, employer_id) DO NOTHING;

    -- 18. Job Opportunities
    INSERT INTO job_opportunities (employer_id, title, opportunity_type, description, required_skills, location, deadline, status)
    VALUES
    (v_employer_id, 'Software Development Intern', 'Internship', 'Assist in building internal tools.', 'Java, PostgreSQL, Git', 'Makati City', '2026-12-31', 'Open')
    RETURNING opportunity_id INTO v_opp_intern;

    INSERT INTO job_opportunities (employer_id, title, opportunity_type, description, required_skills, location, deadline, status)
    VALUES (v_employer_id, 'Junior Java Developer', 'Employment', 'Entry-level Java developer role.', 'Java, JavaFX, SQL', 'Remote', '2026-11-30', 'Open');

    -- 19. Applications
    INSERT INTO applications (opportunity_id, student_id, status, remarks)
    VALUES (v_opp_intern, v_student_id, 'Pending', 'Interested in the internship program')
    ON CONFLICT (opportunity_id, student_id) DO NOTHING;

    -- 20. Academic Support
    INSERT INTO academic_support (student_id, instructor_id, subject_id, support_type, support_date, reason, action_taken, outcome, remarks)
    VALUES (v_student_id, v_instructor_id, v_sub_302, 'Remedial', '2026-09-12', 'Difficulty with SQL joins', 'Extra exercises on joins', 'Improved', 'Follow up next week');

    -- 21. Institutional Records
    INSERT INTO institutional_records (record_type, identifier_number, institutional_email, full_name, department_or_program, is_active) VALUES
    ('STUDENT', '2023-0001', 'student@nextit.edu', 'Alex Reyes', 'BS Information Technology', TRUE),
    ('STUDENT', '2023-0002', 'student2@nextit.edu', 'Bianca Cruz', 'BS Information Technology', TRUE),
    ('STUDENT', '2026-0042', 'carlos.mendoza@nextit.edu', 'Carlos Mendoza', 'BS Computer Science', TRUE),
    ('STUDENT', '2026-0099', 'diane.tan@dlsu.edu.ph', 'Diane Tan', 'BS Information Systems', TRUE),
    ('STUDENT', '25-16522', '25-16522@g.bastate-u.edu.ph', 'BatStateU Scholar', 'BS Information Technology', TRUE),
    ('INSTRUCTOR', 'EMP-1001', 'instructor@nextit.edu', 'Maria Santos', 'Computer Science', TRUE),
    ('INSTRUCTOR', 'EMP-1002', 'roberto.garcia@nextit.edu', 'Roberto Garcia', 'Information Technology', TRUE)
    ON CONFLICT (identifier_number) DO NOTHING;

    -- 22. Verification Requests
    INSERT INTO verification_requests (user_id, role, id_number, institutional_email, organization_name, business_registration_number, document_reference, submission_notes, status, risk_score, risk_flags, reviewed_at) VALUES
    (v_student_user, 'STUDENT', '2023-0001', 'student@nextit.edu', 'NextIT Institute of Technology', NULL, 'Official Registrar Enrolment Card', 'Automated institutional registrar validation successful.', 'VERIFIED', 0, 'REGISTRAR_VERIFIED_EXACT_MATCH', NOW()),
    (v_student2_user, 'STUDENT', '2023-0002', 'student2@nextit.edu', 'NextIT Institute of Technology', NULL, 'Official Registrar Enrolment Card', 'Automated institutional registrar validation successful.', 'VERIFIED', 0, 'REGISTRAR_VERIFIED_EXACT_MATCH', NOW()),
    (v_batstate_user, 'STUDENT', '25-16522', '25-16522@g.bastate-u.edu.ph', 'Batangas State University', NULL, 'Official Registrar Enrolment Card', 'Automated institutional registrar validation successful.', 'VERIFIED', 0, 'REGISTRAR_VERIFIED_EXACT_MATCH', NOW()),
    (v_instructor_user, 'INSTRUCTOR', 'EMP-1001', 'instructor@nextit.edu', 'College of Computer Studies', NULL, 'Faculty Appointment Form CS-2024', 'Faculty credentials confirmed against institutional records.', 'VERIFIED', 5, 'REGISTRAR_FACULTY_CONFIRMED', NOW()),
    (v_employer_user, 'EMPLOYER', NULL, 'employer@techcorp.com', 'TechCorp Solutions', 'SEC-CS2021008891-TIN009', 'SEC Certificate of Incorporation & DTI Permit', 'Corporate registration verified.', 'VERIFIED', 10, 'CORPORATE_DOMAIN_DETECTED', NOW());

    -- 23. Security Audit Logs
    INSERT INTO security_audit_logs (user_id, actor_username, action, target_user_id, ip_address, details, severity) VALUES
    (v_instructor_user, 'instructor1', 'SYSTEM_INITIALIZED', NULL, '127.0.0.1', 'Security and identity verification system initialized.', 'INFO'),
    (v_student_user, 'student1', 'LOGIN_SUCCESS', v_student_user, '127.0.0.1', 'Successful authentication for verified student account.', 'INFO');

END $$;

