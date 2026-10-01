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
    @Autowired SchedulingService scheduling; @Autowired co.com.fcv.training.citas.application.AppointmentLifecycleService lifecycle; @Autowired JdbcTemplate jdbc;
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
    @Test void rescheduleApprovalAtomicallySwapsSlotsAndKeepsHistoryAuthorized(){
        Fixture f=fixture("swap"); Long appointment=f.approved();
        var request=lifecycle.request(f.patient,appointment,f.date,LocalTime.of(10,0)); Long requestId=((Number)request.get("id")).longValue();
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=?",Integer.class,appointment)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from rescheduling_slots where rescheduling_request_id=?",Integer.class,requestId)).isEqualTo(2);
        assertThat(scheduling.availability(f.location,f.specialty,f.professional,f.date).stream().noneMatch(slot->slot.startAt().equals(LocalDateTime.of(f.date,LocalTime.of(10,0))))).isTrue();
        assertThat(scheduling.availability(f.location,f.specialty,f.professional,f.date).stream().anyMatch(slot->slot.startAt().equals(LocalDateTime.of(f.date,LocalTime.of(9,0))))).isTrue();
        assertThatThrownBy(()->scheduling.reserve(f.otherPatient,f.professional,f.location,f.specialty,LocalDateTime.of(f.date,LocalTime.of(10,0)),"Synthetic competing booking")).isInstanceOf(SchedulingFailure.class).satisfies(e->assertThat(((SchedulingFailure)e).kind()).isEqualTo(SchedulingFailure.Kind.CONFLICT));
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=?",Integer.class,appointment)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from rescheduling_slots where rescheduling_request_id=?",Integer.class,requestId)).isEqualTo(2);
        assertThatThrownBy(()->lifecycle.detail(f.otherPatient,appointment)).isInstanceOf(SchedulingFailure.class);
        assertThat(lifecycle.agenda(f.professionalUser,f.date,f.date,f.location).getFirst()).doesNotContainKey("patient");
        lifecycle.decide(f.admin,requestId,"APPROVE",null);
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=? and start_at=?",Integer.class,appointment,LocalDateTime.of(f.date,LocalTime.of(8,0)))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=? and start_at=?",Integer.class,appointment,LocalDateTime.of(f.date,LocalTime.of(10,0)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select decision_source from rescheduling_requests where id=?",String.class,requestId)).isEqualTo("ADMIN");
        assertThat(lifecycle.history(f.patient,appointment,java.util.Set.of("USER"))).isNotEmpty();
        assertThat(lifecycle.history(f.patient,appointment,java.util.Set.of("USER","PROFESSIONAL"))).isNotEmpty();
        assertThatThrownBy(()->lifecycle.history(f.otherPatient,appointment,java.util.Set.of("USER"))).isInstanceOf(SchedulingFailure.class);
    }
    @Test void rescheduleRejectionReleasesOnlyNewHoldAndCancellationClosesPendingRequest(){
        Fixture f=fixture("reject"); Long appointment=f.approved();var request=lifecycle.request(f.patient,appointment,f.date,LocalTime.of(10,0));Long rid=((Number)request.get("id")).longValue();lifecycle.decide(f.admin,rid,"REJECT","No se puede trasladar");
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=? and start_at=?",Integer.class,appointment,LocalDateTime.of(f.date,LocalTime.of(8,0)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=? and start_at=?",Integer.class,appointment,LocalDateTime.of(f.date,LocalTime.of(10,0)))).isZero();
        assertThat(jdbc.queryForObject("select decision_source from rescheduling_requests where id=?",String.class,rid)).isEqualTo("ADMIN");
        var pending=lifecycle.request(f.patient,appointment,f.date,LocalTime.of(10,0));Long pendingId=((Number)pending.get("id")).longValue();
        lifecycle.cancel(f.patient,appointment,"Ya no asistiré");
        assertThat(jdbc.queryForObject("select code from rescheduling_statuses s join rescheduling_requests r on r.status_id=s.id where r.id=?",String.class,pendingId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select decision_source from rescheduling_requests where id=?",String.class,pendingId)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("select count(*) from professional_slots where appointment_id=?",Integer.class,appointment)).isZero();
        assertThatThrownBy(()->lifecycle.decide(f.admin,pendingId,"APPROVE",null)).isInstanceOf(SchedulingFailure.class).satisfies(e->assertThat(((SchedulingFailure)e).kind()).isEqualTo(SchedulingFailure.Kind.CONFLICT));
        assertThatThrownBy(()->lifecycle.decide(f.admin,pendingId+999999,"APPROVE",null)).isInstanceOf(SchedulingFailure.class).satisfies(e->assertThat(((SchedulingFailure)e).kind()).isEqualTo(SchedulingFailure.Kind.NOT_FOUND));
    }
    @Test void professionalClosesOnlyAssignedApprovedAppointmentAfterScheduledEnd(){Fixture f=fixture("close");Long appointment=f.approved();LocalDateTime future=LocalDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(2);jdbc.update("update appointments set scheduled_start_at=?,scheduled_end_at=? where id=?",future.minusMinutes(60),future,appointment);assertThatThrownBy(()->lifecycle.close(f.professionalUser,appointment,"COMPLETED")).isInstanceOf(SchedulingFailure.class);assertThatThrownBy(()->lifecycle.close(f.otherPatient,appointment,"COMPLETED")).isInstanceOf(Exception.class);LocalDateTime past=LocalDateTime.now(ZoneId.of("America/Bogota")).minusMinutes(2);jdbc.update("update appointments set scheduled_start_at=?,scheduled_end_at=? where id=?",past.minusMinutes(60),past,appointment);lifecycle.close(f.professionalUser,appointment,"NO_SHOW");assertThat(jdbc.queryForObject("select s.code from appointments a join appointment_statuses s on s.id=a.status_id where a.id=?",String.class,appointment)).isEqualTo("NO_SHOW");assertThat(lifecycle.history(f.patient,appointment,java.util.Set.of("USER"))).anyMatch(h->"NO_SHOW".equals(h.get("newStatus"))&&"PROFESSIONAL".equals(h.get("source")));}
    @Test void concurrentRescheduleRequestsRetainOnlyOneNewSlotHold() throws Exception {Fixture f=fixture("concurrent");Long appointment=f.approved();CountDownLatch gate=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);try{Callable<String>attempt=()->{gate.await();try{lifecycle.request(f.patient,appointment,f.date,LocalTime.of(10,0));return "created";}catch(SchedulingFailure e){if(e.kind()!=SchedulingFailure.Kind.CONFLICT)throw e;return "conflict";}};Future<String>a=pool.submit(attempt),b=pool.submit(attempt);gate.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder("created","conflict");assertThat(jdbc.queryForObject("select count(*) from rescheduling_requests r join rescheduling_statuses s on s.id=r.status_id where r.appointment_id=? and s.code='PENDING'",Integer.class,appointment)).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from rescheduling_slots rs join rescheduling_requests r on r.id=rs.rescheduling_request_id join rescheduling_statuses s on s.id=r.status_id where r.appointment_id=? and s.code='PENDING'",Integer.class,appointment)).isEqualTo(2);}finally{pool.shutdownNow();}}
    private Fixture fixture(String label){String suffix=label+UUID.randomUUID().toString().replace("-","").substring(0,16);Long pro=scheduling.createProfessional("Pro","Lifecycle","CC","PL"+suffix,"pl"+suffix+"@example.test","300","hash","PC"+suffix,"LIC"+suffix);Long proUser=jdbc.queryForObject("select user_id from professionals where id=?",Long.class,pro);Long spec=scheduling.createSpecialty("S"+suffix,"Especialidad "+suffix,60,false).id();Long loc=jdbc.queryForObject("select id from locations where active=true limit 1",Long.class);scheduling.setProfessionalSpecialties(pro,List.of(spec),spec);scheduling.setProfessionalLocations(pro,List.of(loc));LocalDate day=LocalDate.now().plusDays(8);scheduling.createBlock(proUser,loc,day,LocalTime.of(8,0),LocalTime.of(12,0));Long patient=user("patient-"+suffix+"@example.test","U"+suffix),other=user("other-"+suffix+"@example.test","O"+suffix),admin=user("admin-"+suffix+"@example.test","A"+suffix);return new Fixture(pro,proUser,spec,loc,day,patient,other,admin);}
    private class Fixture {
        final Long professional,professionalUser,specialty,location,patient,otherPatient,admin;
        final LocalDate date;
        Fixture(Long professional,Long professionalUser,Long specialty,Long location,LocalDate date,Long patient,Long otherPatient,Long admin){this.professional=professional;this.professionalUser=professionalUser;this.specialty=specialty;this.location=location;this.date=date;this.patient=patient;this.otherPatient=otherPatient;this.admin=admin;}
        Long approved(){SchedulingService.Appointment a=scheduling.reserve(patient,professional,location,specialty,LocalDateTime.of(date,LocalTime.of(8,0)),"Synthetic reason");return scheduling.decide(admin,a.id(),"APPROVE",null).id();}
    }
    private Long user(String email,String document) { jdbc.update("insert into users(first_name,last_name,document_type,document_number,email,phone,password_hash,active,email_verified) values ('Test','User','CC',?,?,?,'hash',true,false)",document,email,"300"); return jdbc.queryForObject("select id from users where email=?",Long.class,email); }
}
