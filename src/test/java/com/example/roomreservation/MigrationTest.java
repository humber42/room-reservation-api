package com.example.roomreservation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationTest {
    @Test
    void upgradesExistingRoomsAndEnforcesDatabaseUniqueness() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url,"sa",""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO rooms(name,capacity) VALUES ('Sala Norte',4)");
            Flyway.configure().dataSource(url,"sa","").load().migrate();
            try (var rows = sql.executeQuery("SELECT name,capacity,name_key FROM rooms")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Sala Norte");
                assertThat(rows.getInt(2)).isEqualTo(4);
                assertThat(rows.getString(3)).isEqualTo("SALA NORTE");
                assertThat(rows.next()).isFalse();
            }
            SQLException error = assertThrows(SQLException.class,
                    () -> sql.executeUpdate("INSERT INTO rooms(name,capacity) VALUES ('sala norte',3)"));
            assertThat(error.getSQLState()).isEqualTo("23505");
        }
    }

    @Test
    void existingCaseDuplicatesBlockUpgradeWithoutDeletingRooms() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url,"sa",""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO rooms(name,capacity) VALUES ('Sala',4),('sala',3)");
            FlywayException error = assertThrows(FlywayException.class,
                    () -> Flyway.configure().dataSource(url,"sa","").load().migrate());
            assertThat(error).hasMessageContaining("V2__unique_room_name_ignore_case.sql");
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM rooms")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
