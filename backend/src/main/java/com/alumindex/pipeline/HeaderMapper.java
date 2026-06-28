package com.alumindex.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Makes the import tolerant of arbitrary CSV/XLSX headers (UC004).
 *
 * Resolution order per header: exact canonical name → synonym table →
 * keyword heuristics. If no name column can be identified at all, the
 * header list (plus up to 3 sample rows) is sent to the LLM for a final
 * mapping attempt before the file is rejected.
 *
 * Derivations applied per row after mapping:
 * - full_name missing  → first_name + last_name
 * - first/last missing → split from full_name
 * - captured_date missing → import date (today)
 * - rows with every cell blank are dropped
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeaderMapper {

    private final LlmNormalisationService llm;

    static final Set<String> CANONICAL = Set.of(
            "full_name", "first_name", "last_name", "captured_date",
            "linkedin_url", "employment_title", "employment_company",
            "company_standardized_name", "employment_start_month",
            "employment_start_year", "company_size", "company_type",
            "company_industry", "location_city", "location_state",
            "location_country", "education_degree", "education_major",
            "education_end_year", "university_name");

    private static final Map<String, String> SYNONYMS = buildSynonyms();

    /** Alumni-domain fields that distinguish a graduate dataset from an unrelated file. */
    private static final Set<String> ALUMNI_SIGNALS = Set.of(
            "linkedin_url", "employment_title", "employment_company",
            "company_standardized_name", "company_industry", "company_size",
            "company_type", "employment_start_year", "employment_start_month",
            "education_degree", "education_major", "education_end_year",
            "university_name");

    public List<Map<String, String>> mapRows(List<Map<String, String>> rows) {
        if (rows.isEmpty()) return rows;

        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        Map<String, String> mapping = resolveMapping(headers, rows);
        validateLooksLikeAlumni(mapping, headers, rows);

        String today = LocalDate.now().toString();
        var out = new ArrayList<Map<String, String>>(rows.size());
        for (var raw : rows) {
            var row = new LinkedHashMap<String, String>();
            raw.forEach((k, v) -> {
                String key = mapping.getOrDefault(k, normalise(k));
                String val = v == null ? "" : v.trim();
                // first non-blank value wins when two source columns map to one field
                row.merge(key, val, (a, b) -> a.isBlank() ? b : a);
            });
            if (row.values().stream().allMatch(String::isBlank)) continue;

            deriveNames(row);
            if (blank(row.get("captured_date"))) row.put("captured_date", today);
            out.add(row);
        }
        return out;
    }

    private Map<String, String> resolveMapping(List<String> headers,
                                               List<Map<String, String>> rows) {
        var mapping = new LinkedHashMap<String, String>();
        var taken = new HashSet<String>();
        for (String h : headers) {
            String canon = canonicalOf(h);
            if (canon != null && taken.add(canon)) {
                mapping.put(h, canon);
            }
        }

        if (!hasNameSignal(taken)) {
            try {
                var llmMapping = llm.mapHeaders(headers,
                        rows.subList(0, Math.min(3, rows.size())));
                llmMapping.forEach((orig, canon) -> {
                    if (CANONICAL.contains(canon) && headers.contains(orig) && taken.add(canon)) {
                        mapping.put(orig, canon);
                    }
                });
                log.info("LLM header mapping applied: {}", llmMapping);
            } catch (Exception e) {
                log.warn("LLM header mapping unavailable: {}", e.getMessage());
            }
        }

        if (!hasNameSignal(taken)) {
            throw new IllegalArgumentException(
                    "Could not identify a name column. Include a column such as "
                    + "full_name / name, or first_name + last_name. Found headers: "
                    + headers);
        }
        return mapping;
    }

    /**
     * Guards against non-alumni uploads (UC004). A name column alone is ambiguous —
     * accounting, contact and inventory files often have one too. Files that already
     * map ≥2 alumni-specific fields are accepted outright (no extra cost); otherwise
     * the headers and a few sample rows are sent to the LLM to confirm the file is
     * alumni data, and it is rejected with a clear message if not. If the classifier
     * is unreachable we accept on the name signal rather than block a valid import.
     */
    private void validateLooksLikeAlumni(Map<String, String> mapping,
                                         List<String> headers,
                                         List<Map<String, String>> rows) {
        long signals = mapping.values().stream().distinct()
                .filter(ALUMNI_SIGNALS::contains).count();
        if (signals >= 2) return; // unmistakably alumni — no LLM needed

        try {
            var c = llm.classifyAlumniFile(headers, rows.subList(0, Math.min(3, rows.size())));
            if (!c.isAlumni()) {
                String type = blank(c.detectedType()) ? "" : " It looks like a " + c.detectedType() + " file.";
                String why  = blank(c.reason())       ? "" : " " + c.reason();
                throw new IllegalArgumentException(
                        "This doesn't appear to be an alumni file." + type + why
                        + " Please upload alumni/graduate records (with details such as "
                        + "graduation year, university, employer, job title or LinkedIn).");
            }
        } catch (LlmNormalisationService.LlmUnavailableException e) {
            log.warn("Alumni-file classification unavailable, accepting on name signal: {}",
                    e.getMessage());
        }
    }

    private static boolean hasNameSignal(Set<String> mapped) {
        return mapped.contains("full_name")
                || (mapped.contains("first_name") || mapped.contains("last_name"));
    }

    private static void deriveNames(Map<String, String> row) {
        String full  = row.getOrDefault("full_name", "");
        String first = row.getOrDefault("first_name", "");
        String last  = row.getOrDefault("last_name", "");

        if (blank(full) && !(blank(first) && blank(last))) {
            row.put("full_name", (first + " " + last).trim());
        } else if (!blank(full) && blank(first) && blank(last)) {
            int sp = full.indexOf(' ');
            row.put("first_name", sp < 0 ? full : full.substring(0, sp));
            row.put("last_name",  sp < 0 ? ""   : full.substring(sp + 1).trim());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** lowercases, strips BOM/quotes, collapses runs of non-alphanumerics to '_'. */
    static String normalise(String header) {
        return header == null ? "" : header
                .replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String canonicalOf(String header) {
        String n = normalise(header);
        if (n.isEmpty()) return null;
        if (CANONICAL.contains(n)) return n;
        String syn = SYNONYMS.get(n);
        if (syn != null) return syn;

        // keyword heuristics for anything the synonym table missed
        if (n.contains("linkedin")) return "linkedin_url";
        if (n.contains("grad") && n.contains("year")) return "education_end_year";
        if (n.contains("class") && n.contains("of")) return "education_end_year";
        if (n.contains("full") && n.contains("name")) return "full_name";
        if (n.contains("first") && n.contains("name")) return "first_name";
        if (n.contains("last") && n.contains("name")) return "last_name";
        if (n.contains("captur") && n.contains("date")) return "captured_date";
        return null;
    }

    private static Map<String, String> buildSynonyms() {
        var m = new HashMap<String, String>();
        put(m, "full_name", "name", "fullname", "alumni_name", "student_name",
                "graduate_name", "complete_name", "candidate_name", "person_name",
                "nama", "nama_penuh");
        put(m, "first_name", "firstname", "given_name", "givenname", "fname",
                "forename", "first");
        put(m, "last_name", "lastname", "surname", "family_name", "familyname",
                "lname", "last");
        put(m, "captured_date", "capture_date", "captureddate", "date_captured",
                "snapshot_date", "as_of_date", "asof_date", "record_date",
                "data_date", "export_date", "exported_date", "scraped_date",
                "scrape_date", "date", "last_updated", "updated_at",
                "updated_date", "timestamp", "tarikh");
        put(m, "linkedin_url", "linkedin", "linkedin_link", "linkedin_profile",
                "li_url", "profile_url", "profile_link", "linkedin_profile_url");
        put(m, "employment_title", "job_title", "jobtitle", "title", "position",
                "role", "designation", "current_title", "current_position",
                "job", "occupation", "current_role", "jawatan");
        put(m, "employment_company", "company", "employer", "company_name",
                "organization", "organisation", "current_company",
                "current_employer", "workplace", "firm", "syarikat", "majikan");
        put(m, "company_industry", "industry", "sector", "industry_name",
                "business_sector", "industri");
        put(m, "company_size", "employees", "headcount", "company_headcount",
                "employee_count");
        put(m, "company_type", "employer_type", "organization_type",
                "organisation_type");
        put(m, "location_city", "city", "town", "bandar");
        put(m, "location_state", "state", "province", "region", "negeri");
        put(m, "location_country", "country", "nation", "negara");
        put(m, "education_degree", "degree", "qualification", "degree_name",
                "education_level", "ijazah");
        put(m, "education_major", "major", "course", "field_of_study",
                "programme", "program", "course_of_study", "specialization",
                "specialisation", "kursus");
        put(m, "education_end_year", "graduation_year", "grad_year",
                "year_of_graduation", "end_year", "completion_year",
                "year_graduated", "graduating_year", "convocation_year",
                "passing_year", "cohort", "cohort_year", "batch",
                "tahun_graduasi");
        put(m, "university_name", "university", "institution", "school",
                "college", "alma_mater", "universiti");
        put(m, "employment_start_year", "start_year", "year_started",
                "joining_year");
        put(m, "employment_start_month", "start_month");
        return Map.copyOf(m);
    }

    private static void put(Map<String, String> m, String canonical, String... synonyms) {
        for (String s : synonyms) m.put(s, canonical);
    }
}
