package com.alumindex.config;

import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps every JDBC connection to set the PostgreSQL session variable
 * app.current_tenant_id, which the RLS policies read via current_tenant_id().
 * Superadmin requests leave TenantContext null → the variable is set to empty,
 * causing RLS policies to return no rows for tenant-scoped tables; however,
 * superadmin queries always use explicit tenant_id parameters and bypass RLS
 * via SECURITY DEFINER functions or direct service-layer filtering.
 */
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    /** RLS GUC is PostgreSQL-specific. Cached so non-Postgres DBs (e.g. H2 in tests) skip it. */
    private volatile Boolean postgres;

    public RlsConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        Connection conn = dataSource.getConnection();
        if (isPostgres(conn)) {
            try (var stmt = conn.createStatement()) {
                String safeId = (tenantId != null) ? tenantId.replaceAll("[^a-fA-F0-9\\-]", "") : "";
                stmt.execute("SET LOCAL app.current_tenant_id = '" + safeId + "'");
            }
        }
        return conn;
    }

    @Override
    public void releaseConnection(String tenantId, Connection connection) throws SQLException {
        if (isPostgres(connection)) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = ''");
            }
        }
        connection.close();
    }

    /** True when the live database is PostgreSQL (Supabase reports as such); cached after first check. */
    private boolean isPostgres(Connection conn) throws SQLException {
        Boolean p = postgres;
        if (p == null) {
            synchronized (this) {
                if (postgres == null) {
                    postgres = "PostgreSQL".equalsIgnoreCase(
                            conn.getMetaData().getDatabaseProductName());
                }
                p = postgres;
            }
        }
        return p;
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return ConnectionProvider.class.equals(unwrapType)
                || MultiTenantConnectionProvider.class.equals(unwrapType)
                || RlsConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) return (T) this;
        throw new UnknownUnwrapTypeException(unwrapType);
    }
}
