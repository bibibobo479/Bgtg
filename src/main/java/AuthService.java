import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import javax.mail.*;
import javax.mail.internet.*;

public class AuthService {

    private static final Gson gson = new Gson();
    private static final SecureRandom random = new SecureRandom();
    private static final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();

    // Конфигурация почты
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String SMTP_USERNAME = "avey8431@gmail.com";
    private static final String SMTP_PASSWORD = "fggx drdh wnvy frfm";

    static class VerificationCode {
        String code;
        long expiryTime;

        VerificationCode(String code) {
            this.code = code;
            this.expiryTime = System.currentTimeMillis() + 10 * 60 * 1000; // 10 минут
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    public static void startAuthServer() throws IOException {
        HttpServer authServer = HttpServer.create(new InetSocketAddress("0.0.0.0", 8083), 0);
        
        authServer.createContext("/api/register", new RegisterHandler());
        authServer.createContext("/api/login", new LoginHandler());
        authServer.createContext("/api/verify", new VerifyHandler());
        authServer.createContext("/api/resend-code", new ResendCodeHandler());
        
        authServer.setExecutor(Executors.newCachedThreadPool());
        authServer.start();
        
        System.out.println("============================================================");
        System.out.println("🔐 Auth сервер запущен на порту 8083");
        System.out.println("============================================================");
        initAuthDatabase();
    }

    private static void initAuthDatabase() {
        try {
            String dbPath = new File("auth.db").getAbsolutePath();
            System.out.println("📂 Путь к базе данных: " + dbPath);
            
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                Statement stmt = conn.createStatement();
                
                stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "device_name TEXT, " +
                    "verified BOOLEAN DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_login TIMESTAMP)");
                
                System.out.println("✅ Auth база данных готова");
                
                // Проверяем существующих пользователей
                ResultSet rs = stmt.executeQuery("SELECT email, verified FROM users");
                while (rs.next()) {
                    System.out.println("📊 Пользователь: " + rs.getString("email") + 
                                     ", verified: " + rs.getBoolean("verified"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка базы данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String readRequestBody(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String[] hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            String saltStr = Base64.getEncoder().encodeToString(salt);
            String hashStr = Base64.getEncoder().encodeToString(hash);
            
            return new String[]{hashStr, saltStr};
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean verifyPassword(String password, String hash, String saltStr) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltStr);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            String computedHash = Base64.getEncoder().encodeToString(hashBytes);
            
            return computedHash.equals(hash);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean sendVerificationEmail(String to, String code) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
            }
        });
        
        session.setDebug(true);
        
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SMTP_USERNAME));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("Подтверждение регистрации в чате");
            
            String htmlContent = String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 30px;'>" +
                "<h2 style='color: #008069; text-align: center;'>Подтверждение email</h2>" +
                "<p style='font-size: 16px;'>Спасибо за регистрацию в нашем чате!</p>" +
                "<p style='font-size: 16px;'>Ваш код подтверждения:</p>" +
                "<div style='background: #008069; color: white; font-size: 32px; font-weight: bold; " +
                "text-align: center; padding: 20px; border-radius: 10px; letter-spacing: 8px; margin: 20px 0;'>%s</div>" +
                "<p style='font-size: 14px; color: #666;'>Код действителен в течение 10 минут.</p>" +
                "<p style='font-size: 14px; color: #666;'>Если вы не регистрировались в чате, просто проигнорируйте это письмо.</p>" +
                "</div></body></html>", code);
            
            message.setContent(htmlContent, "text/html; charset=utf-8");
            Transport.send(message);
            System.out.println("✅ Email отправлен на " + to + " с кодом: " + code);
            return true;
            
        } catch (MessagingException e) {
            System.err.println("❌ Ошибка отправки email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static String generateVerificationCode() {
        return String.format("%06d", random.nextInt(1000000));
    }

    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange.getRequestBody());
                System.out.println("📝 Регистрация: " + requestBody);
                
                Map<String, String> data = gson.fromJson(requestBody, Map.class);
                String username = data.get("username");
                String email = data.get("email");
                String device = data.get("device");
                String password = data.get("password");
                
                // Проверяем существование пользователя
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT email FROM users WHERE email = ?");
                    checkStmt.setString(1, email);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next()) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Email уже зарегистрирован");
                        sendJsonResponse(exchange, 400, error);
                        return;
                    }
                }
                
                String[] hashAndSalt = hashPassword(password);
                
                // Сохраняем пользователя
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO users (username, email, password_hash, salt, device_name, verified) " +
                        "VALUES (?, ?, ?, ?, ?, ?)");
                    pstmt.setString(1, username);
                    pstmt.setString(2, email);
                    pstmt.setString(3, hashAndSalt[0]);
                    pstmt.setString(4, hashAndSalt[1]);
                    pstmt.setString(5, device);
                    pstmt.setBoolean(6, false);
                    pstmt.executeUpdate();
                    
                    System.out.println("✅ Пользователь создан: " + email);
                }
                
                String code = generateVerificationCode();
                verificationCodes.put(email, new VerificationCode(code));
                System.out.println("📧 Код для " + email + ": " + code);
                
                if (sendVerificationEmail(email, code)) {
                    Map<String, String> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("message", "Код подтверждения отправлен на email");
                    sendJsonResponse(exchange, 200, response);
                } else {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Ошибка отправки email");
                    sendJsonResponse(exchange, 500, error);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, String> error = new HashMap<>();
                error.put("error", e.getMessage());
                sendJsonResponse(exchange, 500, error);
            } finally {
                exchange.close();
            }
        }
    }

    static class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange.getRequestBody());
                System.out.println("========== VERIFY REQUEST ==========");
                System.out.println("Body: " + requestBody);
                
                Map<String, String> data = gson.fromJson(requestBody, Map.class);
                String email = data.get("email");
                String code = data.get("code");
                
                System.out.println("Email: " + email);
                System.out.println("Code: " + code);
                
                // Проверяем код
                VerificationCode savedCode = verificationCodes.get(email);
                
                if (savedCode == null) {
                    System.out.println("❌ Код не найден");
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Код не найден. Запросите новый код");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }
                
                if (!savedCode.isValid()) {
                    System.out.println("❌ Код истек");
                    verificationCodes.remove(email);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Код истек. Запросите новый код");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }
                
                if (!savedCode.code.equals(code)) {
                    System.out.println("❌ Неверный код. Ожидался: " + savedCode.code);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Неверный код");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }
                
                // ОБНОВЛЯЕМ verified В БАЗЕ ДАННЫХ
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    // Сначала проверяем, существует ли пользователь
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT email FROM users WHERE email = ?");
                    checkStmt.setString(1, email);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (!rs.next()) {
                        System.out.println("❌ Пользователь не найден: " + email);
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Пользователь не найден");
                        sendJsonResponse(exchange, 404, error);
                        return;
                    }
                    
                    // Обновляем verified = 1
                    PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE users SET verified = 1 WHERE email = ?");
                    updateStmt.setString(1, email);
                    int rowsUpdated = updateStmt.executeUpdate();
                    
                    System.out.println("✅ Обновлено записей: " + rowsUpdated);
                    
                    if (rowsUpdated == 0) {
                        System.out.println("⚠️ Не удалось обновить verified");
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Не удалось подтвердить email");
                        sendJsonResponse(exchange, 500, error);
                        return;
                    }
                    
                    // Проверяем, что обновилось
                    PreparedStatement verifyStmt = conn.prepareStatement(
                        "SELECT verified FROM users WHERE email = ?");
                    verifyStmt.setString(1, email);
                    ResultSet verifyRs = verifyStmt.executeQuery();
                    if (verifyRs.next()) {
                        System.out.println("📊 Новый статус verified: " + verifyRs.getBoolean("verified"));
                    }
                    
                    // Получаем данные пользователя
                    PreparedStatement selectStmt = conn.prepareStatement(
                        "SELECT username, email, device_name FROM users WHERE email = ?");
                    selectStmt.setString(1, email);
                    ResultSet userRs = selectStmt.executeQuery();
                    
                    if (userRs.next()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("username", userRs.getString("username"));
                        userData.put("email", userRs.getString("email"));
                        userData.put("device", userRs.getString("device_name"));
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("status", "success");
                        response.put("user", userData);
                        
                        System.out.println("✅ Успешное подтверждение для: " + email);
                        sendJsonResponse(exchange, 200, response);
                    } else {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Ошибка получения данных");
                        sendJsonResponse(exchange, 500, error);
                    }
                }
                
                // Удаляем использованный код
                verificationCodes.remove(email);
                
            } catch (Exception e) {
                System.err.println("❌ Ошибка в verify: " + e.getMessage());
                e.printStackTrace();
                Map<String, String> error = new HashMap<>();
                error.put("error", e.getMessage());
                sendJsonResponse(exchange, 500, error);
            } finally {
                exchange.close();
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange.getRequestBody());
                System.out.println("📝 Login запрос: " + requestBody);
                
                Map<String, String> data = gson.fromJson(requestBody, Map.class);
                String email = data.get("email");
                String password = data.get("password");
                
                System.out.println("🔑 Попытка входа для: " + email);
                
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT * FROM users WHERE email = ?");
                    pstmt.setString(1, email);
                    ResultSet rs = pstmt.executeQuery();
                    
                    if (!rs.next()) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Неверный email или пароль");
                        sendJsonResponse(exchange, 401, error);
                        return;
                    }
                    
                    boolean verified = rs.getBoolean("verified");
                    System.out.println("📊 Статус verified: " + verified);
                    
                    if (!verified) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Email не подтвержден. Проверьте почту и введите код подтверждения");
                        sendJsonResponse(exchange, 401, error);
                        return;
                    }
                    
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    
                    if (!verifyPassword(password, hash, salt)) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Неверный email или пароль");
                        sendJsonResponse(exchange, 401, error);
                        return;
                    }
                    
                    // Обновляем last_login
                    PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE email = ?");
                    updateStmt.setString(1, email);
                    updateStmt.executeUpdate();
                    
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", rs.getString("username"));
                    userData.put("email", rs.getString("email"));
                    userData.put("device", rs.getString("device_name"));
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("user", userData);
                    
                    System.out.println("✅ Успешный вход для: " + email);
                    sendJsonResponse(exchange, 200, response);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, String> error = new HashMap<>();
                error.put("error", e.getMessage());
                sendJsonResponse(exchange, 500, error);
            } finally {
                exchange.close();
            }
        }
    }

    static class ResendCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange.getRequestBody());
                System.out.println("📝 Resend запрос: " + requestBody);
                
                Map<String, String> data = gson.fromJson(requestBody, Map.class);
                String email = data.get("email");
                
                String code = generateVerificationCode();
                verificationCodes.put(email, new VerificationCode(code));
                System.out.println("📧 Новый код для " + email + ": " + code);
                
                if (sendVerificationEmail(email, code)) {
                    Map<String, String> response = new HashMap<>();
                    response.put("status", "success");
                    sendJsonResponse(exchange, 200, response);
                } else {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Ошибка отправки email");
                    sendJsonResponse(exchange, 500, error);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, String> error = new HashMap<>();
                error.put("error", e.getMessage());
                sendJsonResponse(exchange, 500, error);
            } finally {
                exchange.close();
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, Map<?, ?> response) throws IOException {
        String json = gson.toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void main(String[] args) {
        try {
            startAuthServer();
            System.out.println("============================================================");
            System.out.println("🚀 AUTH СЕРВЕР ГОТОВ К РАБОТЕ");
            System.out.println("============================================================");
            System.out.println("🔐 Endpoints:");
            System.out.println("   POST /api/register");
            System.out.println("   POST /api/login");
            System.out.println("   POST /api/verify");
            System.out.println("   POST /api/resend-code");
            System.out.println("============================================================");
            
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
