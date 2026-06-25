"""EXPLAIN ANALYZE the slow career_events query against Supabase."""
import psycopg2
import time

conn = psycopg2.connect(
    host="aws-1-ap-northeast-1.pooler.supabase.com",
    port=6543,
    dbname="postgres",
    user="postgres.zdhjxnztwsfkgagavxcc",
    password="A2002.k.a.a9@",
)
conn.autocommit = True
cur = conn.cursor()

TID = "b0000000-0000-0000-0000-000000000001"

# Sanity: row counts
cur.execute("SELECT COUNT(*) FROM career_events")
print("career_events rows:", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM alumni WHERE tenant_id = %s", (TID,))
print("alumni rows (UTM):", cur.fetchone()[0])

sql = """
SELECT e.id, a.full_name
FROM career_events e
JOIN alumni a ON a.id = e.alumni_id
WHERE a.tenant_id = %s
  AND e.significance_level = 'high'
  AND e.dismissed = false
  AND (%s::int IS NULL OR a.education_end_year = %s::int)
ORDER BY e.detected_at DESC
"""

t0 = time.time()
cur.execute(sql, (TID, None, None))
rows = cur.fetchall()
print(f"direct query: {len(rows)} rows in {time.time()-t0:.2f}s")

cur.execute("EXPLAIN (ANALYZE, BUFFERS) " + sql, (TID, None, None))
for r in cur.fetchall():
    print(r[0])

conn.close()
