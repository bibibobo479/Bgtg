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
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

public class WebSocketChatServer {
    
    private static final Gson gson = new Gson();
    private static final Map<String, Session> clients = new ConcurrentHashMap<>();
    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final String UPLOAD_DIR = "uploads";
    
    public static void main(String[] args) {
        initDatabase();
        startFileServer();
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("org.glassfish.tyrus.incomingBufferSize", 20 * 1024 * 1024);
        properties.put("org.glassfish.tyrus.maxSessionMessageSize", 20 * 1024 * 1024);
        
        Server server = new Server("0.0.0.0", 8080, "/ws", properties, ChatEndpoint.class);
        
        try {
            server.start();
            System.out.println("============================================================");
            System.out.println("🚀 WEBSOCKET ЧАТ ЗАПУЩЕН");
            System.out.println("============================================================");
            System.out.println("🌐 WebSocket: ws://188.92.28.209:8080/ws/chat");
            System.out.println("📁 Файловый сервер: http://188.92.28.209:8081");
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
    
    private static void startFileServer() {
        try {
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
    
    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }
    
    private static String getFileType(String filename) {
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
    
    static class FileAssembly {
        String fileName;
        String fileType;
        String sender;
        long expectedSize = 0;
        java.io.File tempFile;
        java.io.FileOutputStream fileStream;
        long totalBytes = 0;
        
        void start(String fileName, String fileType, String sender) throws Exception {
            this.fileName = fileName;
            this.fileType = fileType;
            this.sender = sender;
            this.tempFile = java.io.File.createTempFile("upload_", "_" + sanitizeFilename(fileName));
            this.fileStream = new java.io.FileOutputStream(tempFile);
            System.out.println("📁 Создан временный файл: " + tempFile.getAbsolutePath());
        }
        
        void append(byte[] chunk) throws Exception {
            if (fileStream != null) {
                fileStream.write(chunk);
                totalBytes += chunk.length;
            }
        }
        
        String finish() throws Exception {
            if (fileStream != null) {
                fileStream.close();
                String fileId = UUID.randomUUID().toString();
                String safeFilename = fileId + "_" + sanitizeFilename(fileName);
                Path destPath = Paths.get(UPLOAD_DIR, safeFilename);
                Files.move(tempFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                saveFileInfo(fileId, fileName, sender, fileType, totalBytes, destPath.toString());
                System.out.println("✅ Файл сохранен: " + fileName + ", размер: " + totalBytes + ", ID: " + fileId);
                return fileId;
            }
            return null;
        }
        
        void cleanup() {
            if (fileStream != null) {
                try { fileStream.close(); } catch (Exception e) {}
            }
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception e) {}
            }
        }
    }

@ServerEndpoint("/chat/{device}")
public static class ChatEndpoint {
    private static final Map<Session, FileAssembly> fileAssemblies = new ConcurrentHashMap<>();
    
    @OnMessage
    public void onMessage(ByteBuffer message, boolean last, Session session, @PathParam("device") String device) {
        FileAssembly assembly = fileAssemblies.get(session);
        if (assembly == null) {
            // Если сборщика нет - создаем новый автоматически
            assembly = new FileAssembly();
            fileAssemblies.put(session, assembly);
            System.out.println("🆕 Автоматически создан новый сборщик для сессии " + device);
        }
        
        try {
            byte[] chunk = new byte[message.remaining()];
            message.get(chunk);
            assembly.append(chunk);
            
            System.out.println("📦 Получен чанк от " + device + ", размер: " + chunk.length + 
                              ", всего: " + assembly.totalBytes + ", last: " + last);
            
            // Проверяем, получили ли мы все байты
            if (assembly.expectedSize > 0 && assembly.totalBytes >= assembly.expectedSize) {
                System.out.println("🏁 Получены все байты (" + assembly.totalBytes + " из " + assembly.expectedSize + "), завершаем...");
                
                String fileId = assembly.finish();
                
                Map<String, Object> response = new HashMap<>();
                response.put("type", "upload_complete");
                response.put("fileId", fileId);
                response.put("fileName", assembly.fileName);
                response.put("fileType", assembly.fileType);
                response.put("size", assembly.totalBytes);
                
                String json = gson.toJson(response);
                session.getAsyncRemote().sendText(json);
                
                // НЕ удаляем сборщик сразу, чтобы избежать race condition
                // Просто сбрасываем его для следующей загрузки
                FileAssembly oldAssembly = fileAssemblies.remove(session);
                if (oldAssembly != null) {
                    System.out.println("🧹 Сборщик удален для сессии " + device);
                }
                
                System.out.println("✅ Файл загружен: " + assembly.fileName + ", ID: " + fileId);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            FileAssembly failed = fileAssemblies.remove(session);
            if (failed != null) failed.cleanup();
        }
    }
    
   @OnMessage
    public void onMessage(String message, Session session, @PathParam("device") String device) {
        try {
            Map<String, Object> msgData = gson.fromJson(message, Map.class);
            String type = (String) msgData.get("type");

            // Обработка typing индикатора
            if ("typing".equals(type)) {
                boolean isTyping = (boolean) msgData.get("typing");
                System.out.println("⌨️ " + device + " печатает: " + isTyping);

                // Отправляем статус печати всем КРОМЕ отправителя
                Map<String, Object> typingMsg = new HashMap<>();
                typingMsg.put("type", "typing");
                typingMsg.put("sender", device);
                typingMsg.put("typing", isTyping);

                String json = gson.toJson(typingMsg);

                for (Map.Entry<String, Session> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(device) && entry.getValue().isOpen()) {
                        entry.getValue().getAsyncRemote().sendText(json);
                    }
                }
                return;
            }

            // Обработка метаданных файла
            if ("file_metadata".equals(type)) {
                String fileName = (String) msgData.get("fileName");
                String fileType = (String) msgData.get("fileType");
                long fileSize = msgData.containsKey("fileSize") ? ((Number) msgData.get("fileSize")).longValue() : 0;

                FileAssembly assembly = new FileAssembly();
                assembly.start(fileName, fileType, device);
                assembly.expectedSize = fileSize;

                FileAssembly old = fileAssemblies.put(session, assembly);
                if (old != null) old.cleanup();

                System.out.println("📋 Метаданные файла: " + fileName + " (" + fileType + "), размер: " + fileSize + " байт");
                return;
            }

            // Обычное текстовое сообщение
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

            saveMessageToDB(device, text, fileId, fileName, fileType);

            String json = gson.toJson(outgoingMsg);
            System.out.println("📤 Сообщение от " + device + " для " + clients.size() + " клиентов: " + text);

            // Отправляем всем
            for (Map.Entry<String, Session> entry : clients.entrySet()) {
                if (entry.getValue().isOpen()) {
                    entry.getValue().getAsyncRemote().sendText(json);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки текстового сообщения: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastUserList() {
        List<Map<String, Object>> userList = new ArrayList<>();

        for (Map.Entry<String, Session> entry : clients.entrySet()) {
            String device = entry.getKey();
            Session session = entry.getValue();

            Map<String, Object> user = new HashMap<>();
            user.put("device", device);
            user.put("nickname", device); // Можно добавить никнеймы из БД
            user.put("online", session.isOpen());

            userList.add(user);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "users");
        msg.put("users", userList);

        String json = gson.toJson(msg);

        // Отправляем всем
        for (Session s : clients.values()) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(json);
            }
        }
    }

    
    @OnOpen
    public void onOpen(Session session, @PathParam("device") String device) {
        clients.put(device, session);
        users.put(device, new User(device, device));

        System.out.println("🔌 Подключен: " + device);
        System.out.println("📊 Всего клиентов: " + clients.size());

        // Сохраняем в БД
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db");
            PreparedStatement pstmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO users (device_name, nickname, last_seen) VALUES (?, ?, ?)")) {
            pstmt.setString(1, device);
            pstmt.setString(2, device);
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Отправляем историю новому пользователю
        List<Map<String, Object>> history = getMessageHistory(50);
        Map<String, Object> historyMsg = new HashMap<>();
        historyMsg.put("type", "history");
        historyMsg.put("messages", history);
        session.getAsyncRemote().sendText(gson.toJson(historyMsg));

        // Отправляем приветствие
        Map<String, Object> welcomeMsg = new HashMap<>();
        welcomeMsg.put("type", "message");
        welcomeMsg.put("sender", "system");
        welcomeMsg.put("text", "👋 " + device + " присоединился к чату!");
        welcomeMsg.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        String welcomeJson = gson.toJson(welcomeMsg);
        for (Session s : clients.values()) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(welcomeJson);
            }
        }

        // Отправляем обновленный список пользователей ВСЕМ
        broadcastUserList();
    }



    @OnClose
    public void onClose(Session session, @PathParam("device") String device) {
        FileAssembly assembly = fileAssemblies.remove(session);
        if (assembly != null) assembly.cleanup();

        clients.remove(device);
        users.remove(device);

        System.out.println("🔌 Отключен: " + device);
        System.out.println("📊 Осталось клиентов: " + clients.size());

        // Отправляем сообщение об отключении
        Map<String, Object> byeMsg = new HashMap<>();
        byeMsg.put("type", "message");
        byeMsg.put("sender", "system");
        byeMsg.put("text", "👋 " + device + " покинул чат");
        byeMsg.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        String byeJson = gson.toJson(byeMsg);
        for (Session s : clients.values()) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(byeJson);
            }
        }

        // Отправляем обновленный список пользователей
        broadcastUserList();
    }
        
        @OnError
        public void onError(Session session, Throwable error) {
            System.out.println("❌ Ошибка: " + error.getMessage());
            FileAssembly assembly = fileAssemblies.remove(session);
            if (assembly != null) assembly.cleanup();
        }
    }    
    
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
                
                String boundary = contentType.split("boundary=")[1];
                byte[] body = readAllBytes(exchange.getRequestBody());
                
                String bodyStr = new String(body, 0, Math.min(body.length, 16384), 
                                           java.nio.charset.StandardCharsets.ISO_8859_1);
                
                String filename = "file.bin";
                int filenameIdx = bodyStr.indexOf("filename=\"");
                if (filenameIdx > 0) {
                    int fnStart = filenameIdx + 10;
                    int fnEnd = bodyStr.indexOf("\"", fnStart);
                    if (fnEnd > fnStart) {
                        filename = bodyStr.substring(fnStart, fnEnd);
                    }
                }
                
                String sender = "unknown";
                int senderIdx = bodyStr.indexOf("name=\"sender\"");
                if (senderIdx > 0) {
                    int sStart = bodyStr.indexOf("\r\n\r\n", senderIdx) + 4;
                    int sEnd = bodyStr.indexOf("\r\n", sStart);
                    if (sEnd > sStart) {
                        sender = bodyStr.substring(sStart, sEnd);
                    }
                }
                
                int fileStart = -1;
                for (int i = 0; i < body.length - 4; i++) {
                    if (body[i] == '\r' && body[i+1] == '\n' && 
                        body[i+2] == '\r' && body[i+3] == '\n') {
                        fileStart = i + 4;
                        break;
                    }
                }
                
                byte[] boundaryEnd = ("\r\n--" + boundary + "--").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                int fileEnd = -1;
                for (int i = body.length - boundaryEnd.length; i >= 0; i--) {
                    boolean match = true;
                    for (int j = 0; j < boundaryEnd.length; j++) {
                        if (body[i + j] != boundaryEnd[j]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        fileEnd = i;
                        break;
                    }
                }
                
                if (fileStart <= 0 || fileEnd <= fileStart) {
                    sendError(exchange, 400, "File data not found");
                    return;
                }
                
                byte[] fileData = Arrays.copyOfRange(body, fileStart, fileEnd);
                String fileId = UUID.randomUUID().toString();
                String safeFilename = fileId + "_" + sanitizeFilename(filename);
                Path filePath = Paths.get(UPLOAD_DIR, safeFilename);
                
                Files.write(filePath, fileData);
                
                String fileType = getFileType(filename);
                long fileSize = Files.size(filePath);
                
                saveFileInfo(fileId, filename, sender, fileType, fileSize, filePath.toString());
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ok");
                response.put("file_id", fileId);
                response.put("file_name", filename);
                response.put("file_type", fileType);
                response.put("size", fileSize);
                
                String json = gson.toJson(response);
                byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(jsonBytes.length));
                exchange.sendResponseHeaders(200, jsonBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonBytes);
                    os.flush();
                }
                
                System.out.println("📁 Файл сохранен (HTTP): " + filename + " (" + fileType + ") от " + sender);
                
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, e.getMessage());
            } finally {
                exchange.close();
            }
        }
        
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            String json = gson.toJson(error);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
    
    static class FileDownloadHandler implements HttpHandler {
        private final boolean forceDownload;
        
        public FileDownloadHandler(boolean forceDownload) {
            this.forceDownload = forceDownload;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);
            
            String filePath = null;
            String filename = null;
            
            String sql = "SELECT * FROM files WHERE file_id = ?";
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
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
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
            if (baos.size() > 100 * 1024 * 1024) {
                throw new IOException("File too large (max 100MB)");
            }
        }
        return baos.toByteArray();
    }
}
