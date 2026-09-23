package co.com.fcv.training.citas;

import co.com.fcv.training.citas.application.SchedulingFailure;
import co.com.fcv.training.citas.application.SchedulingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class SchedulingServiceIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername); r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("app.jwt.access-secret", () -> "a".repeat(40)); r.add("app.jwt.refresh-secret", () -> "b".repeat(40)); r.add("app.cookie.secure", () -> true); r.add("app.cookie.same-site", () -> "None");
    }
    @Autowired SchedulingService scheduling; @Autowired JdbcTemplate jdbc;
    @Test void retainsConsecutiveSlotsAndReleasesThemAfterAdministrativeRejection() {
        String suffix=UUID.randomUUID().toString();
        Long professionalUser=user("prof-"+suffix+"@example.test",suffix); Long professional=scheduling.createProfessional("Pro","Fes","CC","P"+suffix,"pro2-"+suffix+"@example.test","300", "hash", "PC"+suffix,"LIC"+suffix);
        // createProfessional creates a second user; use that owner for the professional block
        Long owner=jdbc.queryForObject("select user_id from professionals where id=?",Long.class,professional);
        Long specialty=scheduling.createSpecialty("ESP"+suffix,"Especialidad "+suffix,60,false).id(); Long location=jdbc.queryForObject("select id from locations where active=true limit 1",Long.class);
        scheduling.setProfessionalSpecialties(professional,List.of(specialty),specialty); scheduling.setProfessionalLocations(professional,List.of(location));
        LocalDate date=LocalDate.now().plusDays(2); scheduling.createBlock(owner,location,date,LocalTime.of(8,0),LocalTime.of(10,0));
        assertThat(scheduling.availability(location,specialty,professional,date)).anyMatch(a -> a.startAt().equals(LocalDateTime.of(date,LocalTime.of(8,0))) && a.endAt().equals(LocalDateTime.of(date,LocalTime.of(9,0))));
        Long patient=user("patient-"+suffix+"@example.test","U"+suffix); Long admin=user("admin-"+suffix+"@example.test","A"+suffix);
        SchedulingService.Appointment appointment=scheduling.reserve(patient,professional,location,specialty,LocalDateTime.of(date,LocalTime.of(8,0)),"Prueba");
        assertThat(appointment.status()).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=?",Integer.class,appointment.id())).isEqualTo(2);
        assertThatThrownBy(() -> scheduling.reserve(patient,professional,location,specialty,LocalDateTime.of(date,LocalTime.of(8,0)),"Duplicada")).hasMessageContaining("Franja");
        assertThat(scheduling.decide(admin,appointment.id(),"REJECT","Sin disponibilidad clínica").status()).isEqualTo("REJECTED");
        assertThat(scheduling.availability(location,specialty,professional,date)).anyMatch(a -> a.startAt().equals(LocalDateTime.of(date,LocalTime.of(8,0))));
    }

    @Test void concurrentUsersProduceOneApprovedReservationAndOneConflict() throws Exception {
        String suffix=UUID.randomUUID().toString();
        Long professional=scheduling.createProfessional("Pro","General","CC","G"+suffix,"general-"+suffix+"@example.test","300", "hash", "GC"+suffix,"GL"+suffix);
        Long owner=jdbc.queryForObject("select user_id from professionals where id=?",Long.class,professional);
        Long specialty=jdbc.queryForObject("select id from specialties where code='MEDICINA_GENERAL'",Long.class);
        Long location=jdbc.queryForObject("select id from locations where active=true limit 1",Long.class);
        scheduling.setProfessionalSpecialties(professional,List.of(specialty),specialty);
        scheduling.setProfessionalLocations(professional,List.of(location));
        LocalDate date=LocalDate.now().plusDays(3);
        LocalDateTime start=LocalDateTime.of(date,LocalTime.of(8,0));
        scheduling.createBlock(owner,location,date,LocalTime.of(8,0),LocalTime.of(9,0));
        Long firstPatient=user("patient-one-"+suffix+"@example.test","P1"+suffix);
        Long secondPatient=user("patient-two-"+suffix+"@example.test","P2"+suffix);
        CountDownLatch startGate=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt=( ) -> {
                startGate.await();
                try {
                    return scheduling.reserve(firstPatient,professional,location,specialty,start,"Concurrente").status();
                } catch (SchedulingFailure failure) {
                    if (failure.kind()!=SchedulingFailure.Kind.CONFLICT) throw failure;
                    return "409";
                }
            };
            List<Future<String>> attempts=new ArrayList<>();
            attempts.add(pool.submit(attempt));
            attempts.add(pool.submit(() -> {
                startGate.await();
                try {
                    return scheduling.reserve(secondPatient,professional,location,specialty,start,"Concurrente").status();
                } catch (SchedulingFailure failure) {
                    if (failure.kind()!=SchedulingFailure.Kind.CONFLICT) throw failure;
                    return "409";
                }
            }));
            startGate.countDown();
            List<String> results=List.of(attempts.get(0).get(),attempts.get(1).get());
            assertThat(results).containsExactlyInAnyOrder("APPROVED","409");
            assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id is not null and start_at=?",Integer.class,start)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointments where professional_id=? and scheduled_start_at=?",Integer.class,professional,start)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
    private Long user(String email,String document) { jdbc.update("insert into users(first_name,last_name,document_type,document_number,email,phone,password_hash,active,email_verified) values ('Test','User','CC',?,?,?,'hash',true,false)",document,email,"300"); return jdbc.queryForObject("select id from users where email=?",Long.class,email); }
}
