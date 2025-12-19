package org.example.database.repo;

import org.example.database.Db;

import java.sql.*;

public class VideoSessionRepo {

    private static final String SQL_START = """
        INSERT INTO video_session (started_at_ms, fps, width, height, codec, note)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_END = """
        UPDATE video_session
        SET ended_at_ms = ?
        WHERE id = ? AND ended_at_ms IS NULL
        """;

    public long startSession(long startedAtMs,
                             int fps,
                             Integer width,
                             Integer height,
                             String codec,
                             String note) {

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_START, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, startedAtMs);
            ps.setInt(2, fps);

            if (width == null) ps.setNull(3, Types.SMALLINT);
            else ps.setInt(3, width);

            if (height == null) ps.setNull(4, Types.SMALLINT);
            else ps.setInt(4, height);

            ps.setString(5, (codec == null || codec.isBlank()) ? "JPEG" : codec);
            if (note == null || note.isBlank()) ps.setNull(6, Types.VARCHAR);
            else ps.setString(6, note);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }

        } catch (Exception e) {
            System.out.println("⚠ DB startSession failed: " + e.getMessage());
        }

        return -1;
    }

    public void endSession(long sessionId, long endedAtMs) {
        if (sessionId <= 0) return;

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_END)) {

            ps.setLong(1, endedAtMs);
            ps.setLong(2, sessionId);
            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("⚠ DB endSession failed: " + e.getMessage());
        }
    }
}