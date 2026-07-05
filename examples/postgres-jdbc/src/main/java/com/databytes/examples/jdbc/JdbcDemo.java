package com.databytes.examples.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Raw JDBC against the course Postgres. This is the hand-written version of what
 * Day 6 did interactively in psql; Day 9 replaces all of this with Hibernate ORM
 * with Panache. No frameworks here on purpose: DriverManager, PreparedStatement,
 * ResultSet, and an explicit transaction so the mechanics are visible.
 *
 * Postgres is published on the host at localhost:5432 (db "orders", user "orders").
 */
public final class JdbcDemo {

    private static final String URL = "jdbc:postgresql://localhost:5432/orders";
    private static final String USER = "orders";
    private static final String PASSWORD = "orders";

    private static final String CREATE_TABLE = """
            create table if not exists applicants (
                id            bigint generated always as identity primary key,
                full_name     varchar(128)  not null,
                national_id   varchar(64)   not null,
                date_of_birth date          not null,
                country       varchar(64)   not null,
                email         varchar(256)  not null,
                status        varchar(16)   not null,
                attributes    jsonb         not null default '{}'::jsonb,
                created_at    timestamptz   not null default now(),
                updated_at    timestamptz   not null default now()
            )
            """;

    private static final String INSERT_APPLICANT = """
            insert into applicants
                (full_name, national_id, date_of_birth, country, email, status, attributes)
            values
                (?, ?, ?, ?, ?, ?, ?::jsonb)
            returning id
            """;

    private static final String SELECT_ALL = """
            select id, full_name, country, status, attributes::text as attributes_text
            from applicants
            order by id
            """;

    private static final String UPDATE_STATUS = """
            update applicants set status = ?, updated_at = now() where id = ?
            """;

    private static final String SELECT_STATUS = """
            select status from applicants where id = ?
            """;

    private JdbcDemo() {
    }

    public static void main(String[] args) throws SQLException {
        System.out.println("== Raw JDBC demo against " + URL + " ==");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {

            createTable(conn);

            long firstId = insertApplicant(conn,
                    "Amira Haddad", "TN-4820193", "1990-04-12", "Tunisia",
                    "amira.haddad@example.com", "PENDING",
                    "{\"riskBand\":\"low\",\"channel\":\"web\"}");

            long secondId = insertApplicant(conn,
                    "Jonas Berg", "SE-9931028", "1985-11-03", "Sweden",
                    "jonas.berg@example.com", "PENDING",
                    "{\"riskBand\":\"medium\",\"channel\":\"mobile\"}");

            System.out.println("Inserted applicants with ids: " + firstId + ", " + secondId);

            selectAll(conn);

            demonstrateTransaction(conn, firstId);
        }

        System.out.println("== Done ==");
    }

    private static void createTable(Connection conn) throws SQLException {
        System.out.println("\n[1] Ensuring the applicants table exists...");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
        System.out.println("    Table ready.");
    }

    private static long insertApplicant(Connection conn, String fullName, String nationalId,
                                        String dateOfBirth, String country, String email,
                                        String status, String attributesJson) throws SQLException {
        System.out.println("\n[2] Inserting applicant '" + fullName + "' (INSERT ... RETURNING id)...");
        try (PreparedStatement ps = conn.prepareStatement(INSERT_APPLICANT)) {
            ps.setString(1, fullName);
            ps.setString(2, nationalId);
            ps.setObject(3, java.time.LocalDate.parse(dateOfBirth));
            ps.setString(4, country);
            ps.setString(5, email);
            ps.setString(6, status);
            ps.setString(7, attributesJson);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong("id");
                System.out.println("    -> generated id = " + id);
                return id;
            }
        }
    }

    private static void selectAll(Connection conn) throws SQLException {
        System.out.println("\n[3] Selecting all applicants and iterating the ResultSet...");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                String row = "    id=%d | %s | %s | %s | attributes=%s".formatted(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("country"),
                        rs.getString("status"),
                        rs.getString("attributes_text"));
                System.out.println(row);
            }
        }
    }

    private static void demonstrateTransaction(Connection conn, long id) throws SQLException {
        System.out.println("\n[4] Transaction demo on applicant id=" + id + "...");
        System.out.println("    current status = " + readStatus(conn, id));

        conn.setAutoCommit(false);
        try {
            System.out.println("    -- setAutoCommit(false); UPDATE status -> 'BLOCKED' (not committed)");
            updateStatus(conn, id, "BLOCKED");
            System.out.println("    status inside txn (uncommitted) = " + readStatus(conn, id));

            System.out.println("    -- conn.rollback()");
            conn.rollback();
            System.out.println("    status after rollback = " + readStatus(conn, id) + "  (change undone)");

            System.out.println("    -- UPDATE status -> 'CLEARED'; conn.commit()");
            updateStatus(conn, id, "CLEARED");
            conn.commit();
            System.out.println("    status after commit = " + readStatus(conn, id) + "  (change persisted)");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void updateStatus(Connection conn, long id, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static String readStatus(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_STATUS)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("status");
            }
        }
    }
}
