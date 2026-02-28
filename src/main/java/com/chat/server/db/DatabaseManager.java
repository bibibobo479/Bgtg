package com.chat.server.db;

import com.chat.server.model.User;
import com.chat.server.model.Message;
import com.chat.server.model.FileInfo;
import com.chat.server.model.UserConnection;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static DatabaseManager instance;
    
    private DatabaseManager() {
        initDatabase();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
    
    private void initDatabase() {
        try (java.sql.Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            // Таблица пользователей
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nickname TEXT," +
                "device_name TEXT UNIQUE," +
                "ip_address TEXT," +
                "first_seen TIMESTAMP," +
                "last_seen TIMESTAMP," +
                "total_messages INTEGER DEFAULT 0," +
                "total_files INTEGER DEFAULT 0)");
            
            // Таблица сообщений
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id TEXT UNIQUE," +
                "sender TEXT," +
                "recipient TEXT," +
                "text TEXT," +
                "file_id TEXT," +
                "file_name TEXT," +
                "file_type TEXT," +
                "timestamp TIMESTAMP)");
            
            // Таблица файлов
            stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "file_id TEXT UNIQUE," +
                "filename TEXT," +
                "sender TEXT," +
                "file_type TEXT," +
                "file_size INTEGER," +
                "saved_path TEXT," +
                "blurhash TEXT," +
                "timestamp TIMESTAMP)");
            
            // Таблица подключений
            stmt.execute("CREATE TABLE IF NOT EXISTS connections (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "ip_address TEXT," +
                "connected_at TIMESTAMP," +
                "disconnected_at TIMESTAMP)");
            
            System.out.println("📊 База данных инициализирована");
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка инициализации БД: " + e.getMessage());
        }
    }
    
    // ==================== USER METHODS ====================
    
    public boolean saveUser(User user) {
        String sql = "INSERT OR REPLACE INTO users (nickname, device_name, ip_address, first_seen, last_seen, total_messages, total_files) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getNickname());
            pstmt.setString(2, user.getDeviceName());
            pstmt.setString(3, user.getIpAddress());
            pstmt.setTimestamp(4, new Timestamp(user.getFirstSeen().getTime()));
            pstmt.setTimestamp(5, new Timestamp(user.getLastSeen().getTime()));
            pstmt.setInt(6, user.getTotalMessages());
            pstmt.setInt(7, user.getTotalFiles());
            
            pstmt.executeUpdate();
            return true;
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка сохранения пользователя: " + e.getMessage());
            return false;
        }
    }
    
    public User getUserByDeviceName(String deviceName) {
        String sql = "SELECT * FROM users WHERE device_name = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setNickname(rs.getString("nickname"));
                user.setDeviceName(rs.getString("device_name"));
                user.setIpAddress(rs.getString("ip_address"));
                user.setFirstSeen(new Date(rs.getTimestamp("first_seen").getTime()));
                user.setLastSeen(new Date(rs.getTimestamp("last_seen").getTime()));
                user.setTotalMessages(rs.getInt("total_messages"));
                user.setTotalFiles(rs.getInt("total_files"));
                return user;
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения пользователя: " + e.getMessage());
        }
        return null;
    }
    
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY last_seen DESC";
        
        try (java.sql.Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setNickname(rs.getString("nickname"));
                user.setDeviceName(rs.getString("device_name"));
                user.setIpAddress(rs.getString("ip_address"));
                user.setFirstSeen(new Date(rs.getTimestamp("first_seen").getTime()));
                user.setLastSeen(new Date(rs.getTimestamp("last_seen").getTime()));
                user.setTotalMessages(rs.getInt("total_messages"));
                user.setTotalFiles(rs.getInt("total_files"));
                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения списка пользователей: " + e.getMessage());
        }
        return users;
    }
    
    public void updateUserLastSeen(String deviceName) {
        String sql = "UPDATE users SET last_seen = ? WHERE device_name = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new Timestamp(new Date().getTime()));
            pstmt.setString(2, deviceName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления времени пользователя: " + e.getMessage());
        }
    }
    
    public void incrementUserMessages(String deviceName) {
        String sql = "UPDATE users SET total_messages = total_messages + 1, last_seen = ? WHERE device_name = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new Timestamp(new Date().getTime()));
            pstmt.setString(2, deviceName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления счетчика сообщений: " + e.getMessage());
        }
    }
    
    public void incrementUserFiles(String deviceName) {
        String sql = "UPDATE users SET total_files = total_files + 1, last_seen = ? WHERE device_name = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new Timestamp(new Date().getTime()));
            pstmt.setString(2, deviceName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления счетчика файлов: " + e.getMessage());
        }
    }
    
    public int getOnlineUsersCount() {
        String sql = "SELECT COUNT(*) FROM users WHERE last_seen > datetime('now', '-2 minutes')";
        
        try (java.sql.Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка подсчета онлайн пользователей: " + e.getMessage());
        }
        return 0;
    }
    
    // ==================== MESSAGE METHODS ====================
    
    public String saveMessage(Message message) {
        String sql = "INSERT INTO messages (message_id, sender, recipient, text, file_id, file_name, file_type, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, message.getMessageId());
            pstmt.setString(2, message.getSender());
            pstmt.setString(3, message.getRecipient());
            pstmt.setString(4, message.getText());
            pstmt.setString(5, message.getFileId());
            pstmt.setString(6, message.getFileName());
            pstmt.setString(7, message.getFileType());
            pstmt.setTimestamp(8, new Timestamp(message.getTimestamp().getTime()));
            
            pstmt.executeUpdate();
            return message.getMessageId();
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка сохранения сообщения: " + e.getMessage());
            return null;
        }
    }
    
    public List<Message> getMessagesByUser(String deviceName, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE sender = ? OR recipient = ? OR recipient = 'all' " +
                     "ORDER BY timestamp DESC LIMIT ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceName);
            pstmt.setString(2, deviceName);
            pstmt.setInt(3, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Message msg = new Message();
                msg.setMessageId(rs.getString("message_id"));
                msg.setSender(rs.getString("sender"));
                msg.setRecipient(rs.getString("recipient"));
                msg.setText(rs.getString("text"));
                msg.setFileId(rs.getString("file_id"));
                msg.setFileName(rs.getString("file_name"));
                msg.setFileType(rs.getString("file_type"));
                msg.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
                messages.add(msg);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения сообщений: " + e.getMessage());
        }
        
        Collections.reverse(messages);
        return messages;
    }
    
    public List<Map<String, String>> getMessageHistory(int limit) {
        List<Map<String, String>> history = new ArrayList<>();
        String sql = "SELECT sender, text, timestamp FROM messages ORDER BY timestamp DESC LIMIT ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Map<String, String> msg = new HashMap<>();
                msg.put("sender", rs.getString("sender"));
                msg.put("text", rs.getString("text"));
                msg.put("time", rs.getString("timestamp"));
                history.add(msg);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения истории: " + e.getMessage());
        }
        
        Collections.reverse(history);
        return history;
    }
    
    public List<Message> getAllMessages(int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages ORDER BY timestamp DESC LIMIT ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Message msg = new Message();
                msg.setMessageId(rs.getString("message_id"));
                msg.setSender(rs.getString("sender"));
                msg.setRecipient(rs.getString("recipient"));
                msg.setText(rs.getString("text"));
                msg.setFileId(rs.getString("file_id"));
                msg.setFileName(rs.getString("file_name"));
                msg.setFileType(rs.getString("file_type"));
                msg.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
                messages.add(msg);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения всех сообщений: " + e.getMessage());
        }
        
        return messages;
    }
    
    // ==================== FILE METHODS ====================
    
    public boolean saveFile(FileInfo fileInfo) {
        String sql = "INSERT INTO files (file_id, filename, sender, file_type, file_size, saved_path, blurhash, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileInfo.getFileId());
            pstmt.setString(2, fileInfo.getFilename());
            pstmt.setString(3, fileInfo.getSender());
            pstmt.setString(4, fileInfo.getFileType());
            pstmt.setLong(5, fileInfo.getFileSize());
            pstmt.setString(6, fileInfo.getSavedPath());
            pstmt.setString(7, fileInfo.getBlurhash());
            pstmt.setTimestamp(8, new Timestamp(fileInfo.getTimestamp().getTime()));
            
            pstmt.executeUpdate();
            return true;
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка сохранения файла: " + e.getMessage());
            return false;
        }
    }
    
    public FileInfo getFileById(String fileId) {
        String sql = "SELECT * FROM files WHERE file_id = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, fileId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                FileInfo file = new FileInfo();
                file.setId(rs.getInt("id"));
                file.setFileId(rs.getString("file_id"));
                file.setFilename(rs.getString("filename"));
                file.setSender(rs.getString("sender"));
                file.setFileType(rs.getString("file_type"));
                file.setFileSize(rs.getLong("file_size"));
                file.setSavedPath(rs.getString("saved_path"));
                file.setBlurhash(rs.getString("blurhash"));
                file.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
                return file;
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения файла: " + e.getMessage());
        }
        return null;
    }
    
    public List<FileInfo> getFilesByUser(String sender, int limit) {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT * FROM files WHERE sender = ? ORDER BY timestamp DESC LIMIT ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setInt(2, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                FileInfo file = new FileInfo();
                file.setId(rs.getInt("id"));
                file.setFileId(rs.getString("file_id"));
                file.setFilename(rs.getString("filename"));
                file.setSender(rs.getString("sender"));
                file.setFileType(rs.getString("file_type"));
                file.setFileSize(rs.getLong("file_size"));
                file.setSavedPath(rs.getString("saved_path"));
                file.setBlurhash(rs.getString("blurhash"));
                file.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
                files.add(file);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения файлов пользователя: " + e.getMessage());
        }
        
        return files;
    }
    
    public List<FileInfo> getAllFiles(int limit) {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT * FROM files ORDER BY timestamp DESC LIMIT ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                FileInfo file = new FileInfo();
                file.setId(rs.getInt("id"));
                file.setFileId(rs.getString("file_id"));
                file.setFilename(rs.getString("filename"));
                file.setSender(rs.getString("sender"));
                file.setFileType(rs.getString("file_type"));
                file.setFileSize(rs.getLong("file_size"));
                file.setSavedPath(rs.getString("saved_path"));
                file.setBlurhash(rs.getString("blurhash"));
                file.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
                files.add(file);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения всех файлов: " + e.getMessage());
        }
        
        return files;
    }
    
    // ==================== CONNECTION METHODS ====================
    
    public boolean saveConnection(UserConnection connection) {
        String sql = "INSERT INTO connections (user_id, ip_address, connected_at) VALUES (?, ?, ?)";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, connection.getUserId());
            pstmt.setString(2, connection.getIpAddress());
            pstmt.setTimestamp(3, new Timestamp(connection.getConnectedAt().getTime()));
            
            pstmt.executeUpdate();
            return true;
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка сохранения подключения: " + e.getMessage());
            return false;
        }
    }
    
    public void updateDisconnection(int connectionId) {
        String sql = "UPDATE connections SET disconnected_at = ? WHERE id = ?";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new Timestamp(new Date().getTime()));
            pstmt.setInt(2, connectionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Ошибка обновления отключения: " + e.getMessage());
        }
    }
    
    public List<UserConnection> getUserConnections(int userId) {
        List<UserConnection> connections = new ArrayList<>();
        String sql = "SELECT * FROM connections WHERE user_id = ? ORDER BY connected_at DESC";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                UserConnection connection = new UserConnection();
                connection.setId(rs.getInt("id"));
                connection.setUserId(rs.getInt("user_id"));
                connection.setIpAddress(rs.getString("ip_address"));
                connection.setConnectedAt(new Date(rs.getTimestamp("connected_at").getTime()));
                
                Timestamp disconnectedAt = rs.getTimestamp("disconnected_at");
                if (disconnectedAt != null) {
                    connection.setDisconnectedAt(new Date(disconnectedAt.getTime()));
                }
                
                connections.add(connection);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения подключений: " + e.getMessage());
        }
        
        return connections;
    }
    
    // ==================== STATISTICS METHODS ====================
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try (java.sql.Connection conn = getConnection()) {
            // Общее количество пользователей
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                stats.put("total_users", rs.getInt(1));
            }
            
            // Онлайн пользователи
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE last_seen > datetime('now', '-2 minutes')")) {
                stats.put("online_users", rs.getInt(1));
            }
            
            // Общее количество сообщений
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
                stats.put("total_messages", rs.getInt(1));
            }
            
            // Общее количество файлов
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM files")) {
                stats.put("total_files", rs.getInt(1));
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения статистики: " + e.getMessage());
        }
        
        return stats;
    }
    
    // ==================== CLEANUP METHODS ====================
    
    public void deleteOldMessages(int days) {
        String sql = "DELETE FROM messages WHERE timestamp < datetime('now', '-' || ? || ' days')";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, days);
            int deleted = pstmt.executeUpdate();
            System.out.println("🧹 Удалено старых сообщений: " + deleted);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка удаления старых сообщений: " + e.getMessage());
        }
    }
    
    public void deleteOldFiles(int days) {
        String sql = "DELETE FROM files WHERE timestamp < datetime('now', '-' || ? || ' days')";
        
        try (java.sql.Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, days);
            int deleted = pstmt.executeUpdate();
            System.out.println("🧹 Удалено старых файлов: " + deleted);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка удаления старых файлов: " + e.getMessage());
        }
    }
}
