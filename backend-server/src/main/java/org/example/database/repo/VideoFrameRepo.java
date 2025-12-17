package org.example.database.repo;

import org.example.database.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class VideoFrameRepo {

    private static final String SQL = """
        INSERT INTO video_frame
        (session_id, received_at_ms, frame_index, mime, jpeg_bytes, bytes_len)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    public void insert(long sessionId, long receivedAtMs, int frameIndex, String mime, byte[] jpegBytes) {
        if (sessionId <= 0) return;

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL)) {

            ps.setLong(1, sessionId);
            ps.setLong(2, receivedAtMs);
            ps.setInt(3, frameIndex);
            ps.setString(4, (mime == null || mime.isBlank()) ? "image/jpeg" : mime);
            ps.setBytes(5, jpegBytes);
            ps.setInt(6, jpegBytes == null ? 0 : jpegBytes.length);

            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("⚠ DB insert video_frame failed: " + e.getMessage());
        }
    }
}