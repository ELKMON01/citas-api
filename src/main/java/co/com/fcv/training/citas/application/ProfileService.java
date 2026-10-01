package co.com.fcv.training.citas.application;

import co.com.fcv.training.citas.domain.Identity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> read(Long userId) {
        Map<String, Object> profile = jdbc.query("""
                select id,first_name,last_name,document_type,document_number,email,phone
                from users where id=? and active=true
                """, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("firstName", rs.getString("first_name"));
            value.put("lastName", rs.getString("last_name"));
            value.put("documentType", rs.getString("document_type"));
            value.put("documentNumber", rs.getString("document_number"));
            value.put("email", rs.getString("email"));
            value.put("phone", rs.getString("phone"));
            return value;
        }, userId).stream().findFirst().orElseThrow(AuthFailure::new);
        profile.put("affiliation", affiliation(userId));
        return profile;
    }

    @Transactional
    public Map<String, Object> update(Long userId, String firstName, String lastName, String phone) {
        if (firstName == null && lastName == null && phone == null) throw new IllegalArgumentException("No hay cambios para guardar");
        Map<String, Object> current = read(userId);
        String nextFirstName = firstName == null ? (String) current.get("firstName") : Identity.required(firstName);
        String nextLastName = lastName == null ? (String) current.get("lastName") : Identity.required(lastName);
        String nextPhone = phone == null ? (String) current.get("phone") : Identity.required(phone);
        jdbc.update("update users set first_name=?,last_name=?,phone=? where id=? and active=true",
                nextFirstName, nextLastName, nextPhone, userId);
        return read(userId);
    }

    public Map<String, Object> affiliation(Long userId) {
        return jdbc.query("""
                select a.id,a.membership_number,a.is_current,p.id plan_id,p.code plan_code,p.name plan_name,
                       e.id eps_id,e.code eps_code,e.name eps_name,r.id regime_id,r.code regime_code,r.name regime_name
                from user_insurance_affiliations a
                join eps_plans p on p.id=a.plan_id
                join eps e on e.id=p.eps_id
                join insurance_regimes r on r.id=p.regime_id
                where a.user_id=? and a.is_current=true order by a.id desc limit 1
                """, (rs, row) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", rs.getLong("id"));
            value.put("membershipNumber", rs.getString("membership_number"));
            value.put("plan", Map.of("id", rs.getLong("plan_id"), "code", rs.getString("plan_code"), "name", rs.getString("plan_name")));
            value.put("eps", Map.of("id", rs.getLong("eps_id"), "code", rs.getString("eps_code"), "name", rs.getString("eps_name")));
            value.put("regime", Map.of("id", rs.getLong("regime_id"), "code", rs.getString("regime_code"), "name", rs.getString("regime_name")));
            return value;
        }, userId).stream().findFirst().orElse(null);
    }

    @Transactional
    public Map<String, Object> setAffiliation(Long userId, Long planId, String membershipNumber) {
        if (planId == null) throw new IllegalArgumentException("El plan es obligatorio");
        Map<String, Object> plan = jdbc.query("select id from eps_plans where id=? and active=true",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong(1)), planId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Plan de afiliación inválido"));
        String membership = membershipNumber == null || membershipNumber.isBlank()
                ? "AUTO-" + userId + "-" + plan.get("id")
                : Identity.required(membershipNumber);
        jdbc.update("update user_insurance_affiliations set is_current=false where user_id=? and is_current=true", userId);
        Long existing = jdbc.query("select id from user_insurance_affiliations where user_id=? and plan_id=? and membership_number=?",
                (rs, row) -> rs.getLong(1), userId, planId, membership).stream().findFirst().orElse(null);
        if (existing == null) {
            jdbc.update("insert into user_insurance_affiliations(user_id,plan_id,membership_number,is_current) values (?,?,?,true)", userId, planId, membership);
        } else {
            jdbc.update("update user_insurance_affiliations set is_current=true where id=?", existing);
        }
        return affiliation(userId);
    }

    @Transactional
    public void removeAffiliation(Long userId) {
        jdbc.update("update user_insurance_affiliations set is_current=false where user_id=? and is_current=true", userId);
    }
}
