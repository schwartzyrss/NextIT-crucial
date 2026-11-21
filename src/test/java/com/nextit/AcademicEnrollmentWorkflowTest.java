package com.nextit;

import com.nextit.exception.AppException;
import com.nextit.model.Enrollment;
import com.nextit.model.Instructor;
import com.nextit.model.Student;
import com.nextit.model.Subject;
import com.nextit.repository.JdbcEnrollmentRepository;
import com.nextit.repository.JdbcSubjectRepository;
import com.nextit.service.AcademicService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class AcademicEnrollmentWorkflowTest {

    private static IntegrationTestData data;
    private static AcademicService academic;
    private static JdbcSubjectRepository subjectRepo;
    private static JdbcEnrollmentRepository enrollmentRepo;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseTestServer.getInstance().useForApplication();
        data = new IntegrationTestData();
        academic = new AcademicService();
        subjectRepo = new JdbcSubjectRepository();
        enrollmentRepo = new JdbcEnrollmentRepository();
    }

    @Test
    void studentCanEnrollInMultipleSubjectsUnderMultipleInstructorsSimultaneously() throws ExecutionException, InterruptedException {
        // 1. Create two separate Instructors
        Instructor instructorA = data.instructor();
        Instructor instructorB = data.instructor();
        assertNotEquals(instructorA.getInstructorId(), instructorB.getInstructorId());

        // 2. Create Subject 1 assigned to Instructor A (Sec A)
        Subject subject1 = new Subject();
        subject1.setSubjectCode("TEST-301-" + System.nanoTime() % 100000);
        subject1.setSubjectName("Advanced Data Structures");
        subject1.setUnits(3.0);
        subject1.setSection("A");
        subject1.setSemester("1st Semester");
        subject1.setAcademicYear("2025-2026");
        subject1.setInstructorId(instructorA.getInstructorId());
        subjectRepo.save(subject1);
        assertTrue(subject1.getSubjectId() > 0);

        // 3. Create Subject 2 assigned to Instructor B (Sec B)
        Subject subject2 = new Subject();
        subject2.setSubjectCode("TEST-302-" + System.nanoTime() % 100000);
        subject2.setSubjectName("Distributed Systems");
        subject2.setUnits(3.0);
        subject2.setSection("B");
        subject2.setSemester("1st Semester");
        subject2.setAcademicYear("2025-2026");
        subject2.setInstructorId(instructorB.getInstructorId());
        subjectRepo.save(subject2);
        assertTrue(subject2.getSubjectId() > 0);

        // 4. Create Student 1
        Student student1 = data.student();

        // 5. Enroll Student 1 in Subject 1 (under Instructor A) and Subject 2 (under Instructor B)
        Enrollment enr1 = academic.enrollStudentInSubject(student1.getStudentId(), subject1.getSubjectId(), "2025-2026", "1st Semester", instructorA.getInstructorId());
        assertNotNull(enr1);
        assertTrue(enr1.getEnrollmentId() > 0);
        assertEquals(instructorA.getInstructorId(), enr1.getInstructorId());

        Enrollment enr2 = academic.enrollStudentInSubject(student1.getStudentId(), subject2.getSubjectId(), "2025-2026", "1st Semester", instructorB.getInstructorId());
        assertNotNull(enr2);
        assertTrue(enr2.getEnrollmentId() > 0);
        assertEquals(instructorB.getInstructorId(), enr2.getInstructorId());

        // 6. Verify enrolledSubjectsForStudent returns both subjects
        List<Subject> enrolledSubjects = academic.enrolledSubjectsForStudent(student1.getStudentId());
        assertTrue(enrolledSubjects.stream().anyMatch(s -> s.getSubjectId() == subject1.getSubjectId()));
        assertTrue(enrolledSubjects.stream().anyMatch(s -> s.getSubjectId() == subject2.getSubjectId()));

        // 7. Verify instructorsForStudent returns BOTH Instructor A and Instructor B
        List<Instructor> associatedInstructors = academic.instructorsForStudent(student1.getStudentId());
        assertTrue(associatedInstructors.stream().anyMatch(i -> i.getInstructorId() == instructorA.getInstructorId()));
        assertTrue(associatedInstructors.stream().anyMatch(i -> i.getInstructorId() == instructorB.getInstructorId()));

        // 8. Create Student 2 and enroll in Subject 1 (shared with Student 1 under Instructor A)
        Student student2 = data.student();
        Enrollment enr3 = academic.enrollStudentInSubject(student2.getStudentId(), subject1.getSubjectId());
        assertTrue(enr3.getEnrollmentId() > 0);

        // 9. Verify Instructor A's roster includes BOTH Student 1 and Student 2
        List<Student> instructorAStudents = academic.studentsForInstructor(instructorA.getInstructorId());
        assertTrue(instructorAStudents.stream().anyMatch(s -> s.getStudentId() == student1.getStudentId()));
        assertTrue(instructorAStudents.stream().anyMatch(s -> s.getStudentId() == student2.getStudentId()));

        // 10. Verify Instructor B's roster has Student 1 but NOT Student 2
        List<Student> instructorBStudents = academic.studentsForInstructor(instructorB.getInstructorId());
        assertTrue(instructorBStudents.stream().anyMatch(s -> s.getStudentId() == student1.getStudentId()));
        assertFalse(instructorBStudents.stream().anyMatch(s -> s.getStudentId() == student2.getStudentId()));

        // 11. Verify duplicate enrollment in the same term is rejected
        assertThrows(AppException.class, () ->
                academic.enrollStudentInSubject(student1.getStudentId(), subject1.getSubjectId(), "2025-2026", "1st Semester", instructorA.getInstructorId()));

        // 12. Verify async enrollment works cleanly
        Subject subject3 = new Subject();
        subject3.setSubjectCode("TEST-303-" + System.nanoTime() % 100000);
        subject3.setSubjectName("Cloud Computing");
        subject3.setUnits(3.0);
        subject3.setInstructorId(instructorB.getInstructorId());
        subjectRepo.save(subject3);

        Enrollment asyncEnr = academic.enrollStudentInSubjectAsync(student2.getStudentId(), subject3.getSubjectId()).get();
        assertNotNull(asyncEnr);
        assertTrue(asyncEnr.getEnrollmentId() > 0);
        assertEquals(subject3.getSubjectId(), asyncEnr.getSubjectId());
    }
}
