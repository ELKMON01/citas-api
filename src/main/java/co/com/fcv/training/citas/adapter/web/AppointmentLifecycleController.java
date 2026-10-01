package co.com.fcv.training.citas.adapter.web;

import co.com.fcv.training.citas.application.AppointmentLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
class AppointmentLifecycleController {
    record Cancellation(@Size(max=500) String reason){}
    record Reschedule(@NotNull LocalDate date,@NotNull LocalTime startTime){}
    record Decision(String decision,@Size(max=500) String reason){}
    record Close(String status){}
    private final AppointmentLifecycleService service;
    AppointmentLifecycleController(AppointmentLifecycleService service){this.service=service;}
    @GetMapping("/user/appointments") @PreAuthorize("hasRole('USER')") List<Map<String,Object>> list(Authentication a,@RequestParam(required=false)String status,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to){return service.list(user(a),status,from,to);}
    @GetMapping("/user/appointments/{id}") @PreAuthorize("hasRole('USER')") Map<String,Object> detail(Authentication a,@PathVariable Long id){return service.detail(user(a),id);}
    @PostMapping("/user/appointments/{id}/cancellation") @PreAuthorize("hasRole('USER')") ResponseEntity<Void> cancel(Authentication a,@PathVariable Long id,@Valid @RequestBody(required=false)Cancellation body){service.cancel(user(a),id,body==null?null:body.reason());return ResponseEntity.noContent().build();}
    @PostMapping("/user/appointments/{id}/rescheduling-requests") @PreAuthorize("hasRole('USER')") ResponseEntity<Map<String,Object>> request(Authentication a,@PathVariable Long id,@Valid @RequestBody Reschedule r){return ResponseEntity.status(201).body(service.request(user(a),id,r.date(),r.startTime()));}
    @GetMapping("/admin/rescheduling-requests") @PreAuthorize("hasRole('ADMIN')") List<Map<String,Object>> pending(@RequestParam(required=false)Long locationId,@RequestParam(required=false)Long professionalId,@RequestParam(required=false)Long specialtyId,@RequestParam(required=false)LocalDate date){return service.pending(locationId,professionalId,specialtyId,date);}
    @PostMapping("/admin/rescheduling-requests/{id}/decision") @PreAuthorize("hasRole('ADMIN')") Map<String,Object> decide(Authentication a,@PathVariable Long id,@Valid @RequestBody Decision r){return service.decide(user(a),id,r.decision()==null?"":r.decision().trim().toUpperCase(Locale.ROOT),r.reason());}
    @GetMapping("/professional/appointments") @PreAuthorize("hasRole('PROFESSIONAL')") List<Map<String,Object>> agenda(Authentication a,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(required=false)Long locationId){return service.agenda(user(a),from,to,locationId);}
    @PatchMapping("/professional/appointments/{id}/status") @PreAuthorize("hasRole('PROFESSIONAL')") ResponseEntity<Void> close(Authentication a,@PathVariable Long id,@Valid @RequestBody Close r){service.close(user(a),id,r.status()==null?"":r.status().trim().toUpperCase(Locale.ROOT));return ResponseEntity.noContent().build();}
    @GetMapping("/appointments/{id}/status-history") @PreAuthorize("hasAnyRole('USER','PROFESSIONAL','ADMIN')") List<Map<String,Object>> history(Authentication a,@PathVariable Long id){Set<String>roles=new HashSet<>();a.getAuthorities().forEach(x->{String r=x.getAuthority();if(r.startsWith("ROLE_"))roles.add(r.substring(5));});return service.history(user(a),id,roles);}
    private Long user(Authentication a){return Long.valueOf(a.getName());}
}
