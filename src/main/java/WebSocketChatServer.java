import org.glassfish.tyrus.server.Server;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.PathParam;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;

public class WebSocketChatServer {
    
    private static final Gson gson = new Gson();
    private static final Map<String, Session> clients = new ConcurrentHashMap<>();
    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final String UPLOAD_DIR = "uploads";
    
    public static void main(String[] args) {
        initDatabase();
        startFileServer(); // Запускаем HTTP сервер для файлов
        
        Server server = new Server("0.0.0.0", 8080, "/ws", null, ChatEndpoint.class);
        
        try {
            server.start();
            System.out.println("============================================================");
            System.out.println("🚀 WEBSOCKET ЧАТ ЗАПУЩЕН");
            System.out.println("============================================================");
            System.out.println("🌐 WebSocket: ws://188.92.28.209:8080/ws/chat");
            System.out.println("📁 Файловый сервер: http://188.92.28.209:8081");
            System.out.println("📤 Upload: http://188.92.28.209:8081/upload");
            System.out.println("📥 Download: http://188.92.28.209:8081/file/{id}");
            System.out.println("============================================================");
            
            Thread.currentThread().join();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
    
    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db")) {
            Statement stmt = conn.createStatement();
            
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "device_name TEXT UNIQUE, " +
                "nickname TEXT, " +
                "last_seen TIMESTAMP)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "text TEXT, " +
                "file_id TEXT, " +
                "file_name TEXT, " +
                "file_type TEXT, " +
                "timestamp TIMESTAMP)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "file_id TEXT UNIQUE, " +
                "filename TEXT, " +
                "sender TEXT, " +
                "file_type TEXT, " +
                "file_size INTEGER, " +
                "saved_path TEXT, " +
                "timestamp TIMESTAMP)");
            
            System.out.println("📊 База данных готова");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Запуск HTTP сервера для файлов
    private static void startFileServer() {
        try {
            // Создаем папку для загрузок
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            
            HttpServer fileServer = HttpServer.create(new InetSocketAddress(8081), 0);
            fileServer.createContext("/upload", new FileUploadHandler());
            fileServer.createContext("/file/", new FileDownloadHandler(false));
            fileServer.createContext("/download/", new FileDownloadHandler(true));
            fileServer.setExecutor(Executors.newCachedThreadPool());
            fileServer.start();
            
            System.out.println("📁 Файловый сервер запущен на порту 8081");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Получение истории сообщений
    private static List<Map<String, Object>> getMessageHistory(int limit) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT sender, text, file_id, file_name, file_type, timestamp FROM messages ORDER BY timestamp DESC LIMIT ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("sender", rs.getString("sender"));
                msg.put("text", rs.getString("text"));
                msg.put("fileId", rs.getString("file_id"));
                msg.put("fileName", rs.getString("file_name"));
                msg.put("fileType", rs.getString("file_type"));
                msg.put("time", rs.getString("timestamp"));
                history.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        Collections.reverse(history);
        return history;
    }
    
    // Сохранение сообщения в БД
    private static void saveMessageToDB(String sender, String text, String fileId, String fileName, String fileType) {
        String sql = "INSERT INTO messages (sender, text, file_id, file_name, file_type, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, sender);
            pstmt.setString(2, text);
            pstmt.setString(3, fileId);
            pstmt.setString(4, fileName);
            pstmt.setString(5, fileType);
            pstmt.setString(6, LocalDateTime.now().toString());
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Сохранение информации о файле
    private static void saveFileInfo(String fileId, String filename, String sender, String fileType, long fileSize, String savedPath) {
        String sql = "INSERT INTO files (file_id, filename, sender, file_type, file_size, saved_path, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db");
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, fileId);
            pstmt.setString(2, filename);
            pstmt.setString(3, sender);
            pstmt.setString(4, fileType);
            pstmt.setLong(5, fileSize);
            pstmt.setString(6, savedPath);
            pstmt.setString(7, LocalDateTime.now().toString());
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Класс пользователя
    static class User {
        String deviceName;
        String nickname;
        String lastSeen;
        
        User(String deviceName, String nickname) {
            this.deviceName = deviceName;
            this.nickname = nickname;
            this.lastSeen = LocalDateTime.now().toString();
        }
    }
    
    @ServerEndpoint("/chat/{device}")
    public static class ChatEndpoint {
        
        @OnOpen
        public void onOpen(Session session, @PathParam("device") String device) {
            clients.put(device, session);
            users.put(device, new User(device, device));
            
            System.out.println("🔌 Подключен: " + device);
            System.out.println("📊 Всего клиентов: " + clients.size());
            
            // Обновляем в базе
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db")) {
                PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO users (device_name, nickname, last_seen) VALUES (?, ?, ?)");
                pstmt.setString(1, device);
                pstmt.setString(2, device);
                pstmt.setString(3, LocalDateTime.now().toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            
            // Отправляем историю
            sendHistory(session);
            
            // Рассылаем список пользователей
            broadcastUserList();
            
            // Приветственное сообщение
            Map<String, Object> welcomeMsg = new HashMap<>();
            welcomeMsg.put("type", "message");
            welcomeMsg.put("sender", "system");
            welcomeMsg.put("text", "👋 " + device + " присоединился к чату!");
            welcomeMsg.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            String json = gson.toJson(welcomeMsg);
            broadcastToAll(json);
        }
        
        @OnMessage
        public void onMessage(String message, Session session, @PathParam("device") String device) {
            System.out.println("📩 От " + device + ": " + message);
            
            try {
                Map<String, Object> msgData = gson.fromJson(message, Map.class);
                String type = (String) msgData.get("type");
                String text = (String) msgData.get("text");
                String fileId = (String) msgData.get("fileId");
                String fileName = (String) msgData.get("fileName");
                String fileType = (String) msgData.get("fileType");
                
                Map<String, Object> outgoingMsg = new HashMap<>();
                outgoingMsg.put("type", "message");
                outgoingMsg.put("sender", device);
                outgoingMsg.put("text", text);
                outgoingMsg.put("fileId", fileId);
                outgoingMsg.put("fileName", fileName);
                outgoingMsg.put("fileType", fileType);
                outgoingMsg.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                
                String json = gson.toJson(outgoingMsg);
                
                // Сохраняем в БД
                saveMessageToDB(device, text, fileId, fileName, fileType);
                
                // Рассылаем всем
                broadcastToAll(json);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        @OnClose
        public void onClose(Session session, @PathParam("device") String device) {
            clients.remove(device);
            users.remove(device);
            System.out.println("🔌 Отключен: " + device);
            
            // Сообщение об отключении
            Map<String, Object> byeMsg = new HashMap<>();
            byeMsg.put("type", "message");
            byeMsg.put("sender", "system");
            byeMsg.put("text", "👋 " + device + " покинул чат");
            byeMsg.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            
            String json = gson.toJson(byeMsg);
            broadcastToAll(json);
            
            broadcastUserList();
        }
        
        @OnError
        public void onError(Session session, Throwable error) {
            System.out.println("❌ Ошибка: " + error.getMessage());
        }
        
        private void sendHistory(Session session) {
            List<Map<String, Object>> history = getMessageHistory(50);
            
            Map<String, Object> historyMsg = new HashMap<>();
            historyMsg.put("type", "history");
            historyMsg.put("messages", history);
            
            String json = gson.toJson(historyMsg);
            session.getAsyncRemote().sendText(json);
        }
        
        private void broadcastUserList() {
            List<Map<String, Object>> userList = new ArrayList<>();
            for (String d : clients.keySet()) {
                Map<String, Object> u = new HashMap<>();
                u.put("device", d);
                u.put("online", true);
                userList.add(u);
            }
            
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "users");
            msg.put("users", userList);
            
            String json = gson.toJson(msg);
            broadcastToAll(json);
        }
        
        private void broadcastToAll(String json) {
            for (Session s : clients.values()) {
                if (s.isOpen()) {
                    s.getAsyncRemote().sendText(json);
                }
            }
        }
    }
    
    // Обработчик загрузки файлов
    static class FileUploadHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            try {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                    sendError(exchange, 400, "Expected multipart/form-data");
                    return;
                }
                
                // Парсинг boundary
                String boundary = contentType.split("boundary=")[1];
                byte[] body = readAllBytes(exchange.getRequestBody());
                String bodyStr = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
                
                // Извлекаем данные файла
                int fileStart = bodyStr.indexOf("\r\n\r\n", bodyStr.indexOf("filename=")) + 4;
                int fileEnd = bodyStr.lastIndexOf("\r\n--" + boundary + "--");
                
                if (fileStart <= 0 || fileEnd <= fileStart) {
                    sendError(exchange, 400, "File data not found");
                    return;
                }
                
                // Извлекаем имя файла
                String filename = "file.bin";
                int filenameIdx = bodyStr.indexOf("filename=\"");
                if (filenameIdx > 0) {
                    int fnStart = filenameIdx + 10;
                    int fnEnd = bodyStr.indexOf("\"", fnStart);
                    if (fnEnd > fnStart) {
                        filename = bodyStr.substring(fnStart, fnEnd);
                    }
                }
                
                // Извлекаем отправителя
                String sender = "unknown";
                int senderIdx = bodyStr.indexOf("name=\"sender\"");
                if (senderIdx > 0) {
                    int sStart = bodyStr.indexOf("\r\n\r\n", senderIdx) + 4;
                    int sEnd = bodyStr.indexOf("\r\n", sStart);
                    if (sEnd > sStart) {
                        sender = bodyStr.substring(sStart, sEnd);
                    }
                }
                
                // Сохраняем файл
                byte[] fileData = Arrays.copyOfRange(body, fileStart, fileEnd);
                String fileId = UUID.randomUUID().toString();
                String safeFilename = fileId + "_" + sanitizeFilename(filename);
                Path filePath = Paths.get(UPLOAD_DIR, safeFilename);
                
                Files.write(filePath, fileData);
                
                // Определяем тип файла
                String fileType = getFileType(filename);
                long fileSize = Files.size(filePath);
                
                // Сохраняем информацию о файле
                saveFileInfo(fileId, filename, sender, fileType, fileSize, filePath.toString());
                
                // Отправляем ответ
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ok");
                response.put("file_id", fileId);
                response.put("file_name", filename);
                response.put("file_type", fileType);
                response.put("size", fileSize);
                
                sendJson(exchange, 200, response);
                
                System.out.println("📁 Файл сохранен: " + filename + " (" + fileType + ") от " + sender);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, e.getMessage());
            } finally {
                exchange.close();
            }
        }
        
        private String getFileType(String filename) {
            String ext = "";
            int lastDot = filename.lastIndexOf('.');
            if (lastDot > 0) {
                ext = filename.substring(lastDot + 1).toLowerCase();
            }
            
            Set<String> imageExts = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "gif", "bmp", "webp"));
            Set<String> videoExts = new HashSet<>(Arrays.asList("mp4", "avi", "mov", "mkv", "webm"));
            Set<String> audioExts = new HashSet<>(Arrays.asList("mp3", "wav", "ogg", "m4a", "aac"));
            
            if (imageExts.contains(ext)) return "image";
            if (videoExts.contains(ext)) return "video";
            if (audioExts.contains(ext)) return "audio";
            return "file";
        }
        
        private String sanitizeFilename(String filename) {
            return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        }
        
        private void sendJson(HttpExchange exchange, int code, Object data) throws IOException {
            String json = gson.toJson(data);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, json.length());
            exchange.getResponseBody().write(json.getBytes());
        }
        
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            sendJson(exchange, code, error);
        }
    }
    
    // Обработчик скачивания файлов
    static class FileDownloadHandler implements HttpHandler {
        private final boolean forceDownload;
        
        public FileDownloadHandler(boolean forceDownload) {
            this.forceDownload = forceDownload;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);
            
            // Ищем файл в БД
            String sql = "SELECT * FROM files WHERE file_id = ?";
            String filePath = null;
            String filename = null;
            
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db");
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, fileId);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    filePath = rs.getString("saved_path");
                    filename = rs.getString("filename");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            
            if (filePath != null && Files.exists(Paths.get(filePath))) {
                Path path_ = Paths.get(filePath);
                byte[] data = Files.readAllBytes(path_);
                
                String mime = Files.probeContentType(path_);
                if (mime != null) {
                    exchange.getResponseHeaders().set("Content-Type", mime);
                }
                
                if (forceDownload) {
                    exchange.getResponseHeaders().set("Content-Disposition", 
                        "attachment; filename=\"" + (filename != null ? filename : fileId) + "\"");
                }
                
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            
            exchange.close();
        }
    }
    
    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
