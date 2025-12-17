package org.example.database.repo;

import org.example.database.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

public class SensorSnapshotRepo {

    // ✅ DB 스키마에 맞춤: received_at_ms, fire, co2, pm25, pm10, pir, source
    private static final String SQL = """
        INSERT INTO sensor_snapshot
        (received_at_ms, fire, co2, pm25, pm10, pir, source)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    public void insert(long receivedAtMs,
                       boolean fire,
                       double co2,
                       double pm25,
                       double pm10,
                       Boolean pir,     // ✅ 테이블이 NULL 허용이라 Boolean
                       String source) {

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL)) {

            ps.setLong(1, receivedAtMs);
            ps.setInt(2, fire ? 1 : 0);

            // 테이블이 NOT NULL이라 null/NaN이면 넣으면 안됨 → 여기선 강제로 값이 들어오게 호출부에서 보정하는 게 정석
            ps.setDouble(3, co2);
            ps.setDouble(4, pm25);
            ps.setDouble(5, pm10);

            if (pir == null) ps.setNull(6, Types.TINYINT);
            else ps.setInt(6, pir ? 1 : 0);

            ps.setString(7, (source == null || source.isBlank()) ? "REAL" : source);

            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("⚠ DB insert sensor_snapshot failed: " + e.getMessage());
        }
    }
}