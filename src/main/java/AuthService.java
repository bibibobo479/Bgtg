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
    private static final String SMTP_USERNAME = "avey8431@gmail.com"; // Замените на свой email
    private static final String SMTP_PASSWORD = "fggx drdh wnvy frfm"; // Замените на пароль приложения

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
        HttpServer authServer = HttpServer.create(new InetSocketAddress(8083), 0);

        authServer.createContext("/api/register", new RegisterHandler());
        authServer.createContext("/api/login", new LoginHandler());
        authServer.createContext("/api/verify", new VerifyHandler());
        authServer.createContext("/api/resend-code", new ResendCodeHandler());

        authServer.setExecutor(Executors.newCachedThreadPool());
        authServer.start();

        System.out.println("🔐 Auth сервер запущен на порту 8083");
        initAuthDatabase();
    }

    private static void initAuthDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
            Statement stmt = conn.createStatement();

            // Таблица пользователей
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

            System.out.println("📊 Auth база данных готова");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Хэширование пароля
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

    // Отправка email с кодом подтверждения
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

        session.setDebug(true); // Покажет весь SMTP-диалог в консоли

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
            System.out.println("📧 Email отправлен на " + to);
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

    // Обработчик регистрации
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                // Читаем тело запроса
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }

                Map<String, String> data = gson.fromJson(requestBody.toString(), Map.class);
                String username = data.get("username");
                String email = data.get("email");
                String device = data.get("device");
                String password = data.get("password");

                // Проверяем, существует ли пользователь
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT email FROM users WHERE email = ?");
                    checkStmt.setString(1, email);
                    ResultSet rs = checkStmt.executeQuery();

                    if (rs.next()) {
                        sendJsonResponse(exchange, 400, Map.of("error", "Email уже зарегистрирован"));
                        return;
                    }
                }

                // Хэшируем пароль
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
                }

                // Генерируем и отправляем код подтверждения
                String code = generateVerificationCode();
                verificationCodes.put(email, new VerificationCode(code));

                if (sendVerificationEmail(email, code)) {
                    sendJsonResponse(exchange, 200, Map.of(
                        "status", "success",
                        "message", "Код подтверждения отправлен на email"
                    ));
                } else {
                    sendJsonResponse(exchange, 500, Map.of(
                        "error", "Ошибка отправки email"
                    ));
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            } finally {
                exchange.close();
            }
        }
    }

    // Обработчик подтверждения email
    static class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }

                Map<String, String> data = gson.fromJson(requestBody.toString(), Map.class);
                String email = data.get("email");
                String code = data.get("code");

                VerificationCode savedCode = verificationCodes.get(email);

                if (savedCode == null || !savedCode.isValid()) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Код недействителен или истек"));
                    return;
                }

                if (!savedCode.code.equals(code)) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Неверный код"));
                    return;
                }

                // Подтверждаем пользователя
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE users SET verified = 1 WHERE email = ?");
                    pstmt.setString(1, email);
                    pstmt.executeUpdate();

                    // Получаем данные пользователя
                    pstmt = conn.prepareStatement(
                        "SELECT username, email, device_name FROM users WHERE email = ?");
                    pstmt.setString(1, email);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("username", rs.getString("username"));
                        userData.put("email", rs.getString("email"));
                        userData.put("device", rs.getString("device_name"));

                        Map<String, Object> response = new HashMap<>();
                        response.put("status", "success");
                        response.put("user", userData);

                        sendJsonResponse(exchange, 200, response);
                    }
                }

                verificationCodes.remove(email);

            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            } finally {
                exchange.close();
            }
        }
    }

    // Обработчик входа
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }

                Map<String, String> data = gson.fromJson(requestBody.toString(), Map.class);
                String email = data.get("email");
                String password = data.get("password");

                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:auth.db")) {
                    PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT * FROM users WHERE email = ?");
                    pstmt.setString(1, email);
                    ResultSet rs = pstmt.executeQuery();

                    if (!rs.next()) {
                        sendJsonResponse(exchange, 401, Map.of("error", "Неверный email или пароль"));
                        return;
                    }

                    if (!rs.getBoolean("verified")) {
                        sendJsonResponse(exchange, 401, Map.of("error", "Email не подтвержден"));
                        return;
                    }

                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");

                    if (!verifyPassword(password, hash, salt)) {
                        sendJsonResponse(exchange, 401, Map.of("error", "Неверный email или пароль"));
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

                    sendJsonResponse(exchange, 200, response);
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            } finally {
                exchange.close();
            }
        }
    }

    // Повторная отправка кода
    static class ResendCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }

                Map<String, String> data = gson.fromJson(requestBody.toString(), Map.class);
                String email = data.get("email");

                String code = generateVerificationCode();
                verificationCodes.put(email, new VerificationCode(code));

                if (sendVerificationEmail(email, code)) {
                    sendJsonResponse(exchange, 200, Map.of("status", "success"));
                } else {
                    sendJsonResponse(exchange, 500, Map.of("error", "Ошибка отправки email"));
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, Map.of("error", e.getMessage()));
            } finally {
                exchange.close();
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, Map<String, ?> response) throws IOException {
        String json = gson.toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
