-- Fix tenant admin password hashes (seed V3 used placeholder hashes)
-- UTM admin / readonly, UM admin, USM admin → correct BCrypt(10) hashes

UPDATE users SET password_hash = '$2a$10$cA7ELNO8sx67cdpXMx6bP.VYMJuAbyqVnBAsSHyuItINmYaW85eFS'
WHERE id IN (
    'c0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000004'
);

UPDATE users SET password_hash = '$2a$10$cjNZ4igSnOYBvi5P4ywnWuJjJr9xD8jjGjHS/pCiMB9PjVb.uqQT2'
WHERE id = 'c0000000-0000-0000-0000-000000000002';

UPDATE users SET password_hash = '$2a$10$ynRLUVmlTzZmAheca9ER8.wtM1lIGeC0CYA25okgzgD4Wn7de6G.y'
WHERE id = 'c0000000-0000-0000-0000-000000000003';
