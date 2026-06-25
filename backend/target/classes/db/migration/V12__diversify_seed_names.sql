-- The V3 seed drew 1,020 names from a 20x20 first/last pool, so combos like
-- "Balkis Osman" repeat up to 34 times and list pages look broken.
-- Insert a deterministic middle name into the 3rd+ occurrence of each
-- duplicated name. Exactly 2 records keep each original name so the
-- AMBIGUOUS_NAME match flag (L3) stays demonstrable.
WITH ranked AS (
    SELECT id, full_name,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, full_name
                              ORDER BY created_at, id) AS rn
    FROM alumni
),
mids AS (
    SELECT ARRAY[
        'Aiman','Danial','Hakim','Iskandar','Luqman','Syafiq','Haziq','Arif',
        'Imran','Zikri','Farhan','Naufal','Rayyan','Mikhail','Adam','Harith',
        'Aqil','Fikri','Irfan','Zharif','Aisyah','Damia','Husna','Iman',
        'Khalisah','Maryam','Nadhirah','Qistina','Safiyyah','Zara','Alia',
        'Batrisyia','Dhia','Eryna','Hannah','Insyirah','Kamilia','Liyana',
        'Medina','Sumayyah'
    ] AS pool
)
UPDATE alumni a
SET full_name = split_part(r.full_name, ' ', 1) || ' ' ||
                (SELECT pool[1 + mod(('x' || substr(md5(a.id::text), 1, 6))::bit(24)::int,
                                     array_length(pool, 1))]
                 FROM mids) ||
                ' ' || substr(r.full_name, position(' ' in r.full_name) + 1)
FROM ranked r
WHERE a.id = r.id
  AND r.rn > 2
  AND position(' ' in r.full_name) > 0;
