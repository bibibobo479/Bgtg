import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class WebClient {
    
    private static final String STATIC_DIR = "/root/websocket-chat/src/main/resources/static";
    private static final String SERVER_URL = "http://188.92.28.209:8080";
    private static final String FILE_SERVER_URL = "http://188.92.28.209:8081";
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 5001), 0);
        
        // Основной обработчик HTML
        server.createContext("/", new HtmlHandler());
        
        // API прокси для сообщений
        server.createContext("/api/register", new ApiProxyHandler("/register"));
        server.createContext("/api/users", new ApiProxyHandler("/users"));
        server.createContext("/api/send", new ApiProxyHandler("/send"));
        server.createContext("/api/receive", new ApiProxyHandler("/receive"));
        server.createContext("/api/history/", new HistoryProxyHandler());
        server.createContext("/api/disconnect", new ApiProxyHandler("/disconnect"));
        
        // Файловые обработчики (прокси на файловый сервер)
        server.createContext("/upload", new FileUploadProxyHandler());
        server.createContext("/file/", new FileDownloadProxyHandler(false));
        server.createContext("/download/", new FileDownloadProxyHandler(true));
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("============================================================");
        System.out.println("📱 МОБИЛЬНЫЙ ЧАТ ЗАПУЩЕН");
        System.out.println("============================================================");
        System.out.println("🌐 Открой в браузере: http://188.92.28.209:5001");
        System.out.println("📁 HTML файл: " + STATIC_DIR + "/index.html");
        System.out.println("📡 WebSocket: ws://188.92.28.209:8080/ws/chat/ИМЯ");
        System.out.println("📤 Upload прокси: /upload -> " + FILE_SERVER_URL + "/upload");
        System.out.println("============================================================");
    }
    
    // Обработчик HTML страницы
    static class HtmlHandler implements HttpHandler {
        
        private byte[] loadHtmlFromFile() {
            try {
                Path filePath = Paths.get(STATIC_DIR, "index.html");
                System.out.println("🔍 Поиск HTML файла: " + filePath.toAbsolutePath());
                
                if (Files.exists(filePath)) {
                    byte[] content = Files.readAllBytes(filePath);
                    System.out.println("✅ HTML файл найден, размер: " + content.length + " байт");
                    return content;
                } else {
                    System.out.println("❌ HTML файл НЕ НАЙДЕН");
                    return "<html><body><h1>Файл не найден</h1></body></html>".getBytes(StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return ("<html><body><h1>Ошибка</h1><p>" + e.getMessage() + "</p></body></html>").getBytes(StandardCharsets.UTF_8);
            }
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            System.out.println("📁 Запрос: " + path);
            
            if (path.equals("/") || path.equals("/index.html")) {
                byte[] htmlContent = loadHtmlFromFile();
                
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.sendResponseHeaders(200, htmlContent.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(htmlContent);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        }
    }
    
    // Прокси для API запросов к основному серверу
    static class ApiProxyHandler implements HttpHandler {
        private final String targetPath;
        
        public ApiProxyHandler(String targetPath) {
            this.targetPath = targetPath;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            
            String url = SERVER_URL + targetPath;
            if (query != null) {
                url += "?" + query;
            }
            
            System.out.println("🔄 API прокси: " + method + " " + url);
            
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                // Копируем заголовки
                exchange.getRequestHeaders().forEach((key, values) -> {
                    if (!key.equalsIgnoreCase("Host")) {
                        conn.setRequestProperty(key, values.get(0));
                    }
                });
                
                // Для POST запросов копируем тело
                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                    try (InputStream is = exchange.getRequestBody();
                         OutputStream os = conn.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                
                int responseCode = conn.getResponseCode();
                
                // Копируем заголовки ответа
                exchange.getResponseHeaders().set("Content-Type", 
                    conn.getContentType() != null ? conn.getContentType() : "application/json");
                exchange.sendResponseHeaders(responseCode, 0);
                
                // Копируем тело ответа
                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ Ошибка API прокси: " + e.getMessage());
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
    
    // Прокси для истории
    static class HistoryProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String path = exchange.getRequestURI().getPath();
                String deviceName = path.substring("/api/history/".length());
                deviceName = URLDecoder.decode(deviceName, "UTF-8");
                
                String query = exchange.getRequestURI().getQuery();
                String url = SERVER_URL + "/history/" + deviceName;
                if (query != null) {
                    url += "?" + query;
                }
                
                System.out.println("📜 История для: " + deviceName);
                
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseCode, 0);
                
                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ Ошибка истории: " + e.getMessage());
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
    
    // Прокси для загрузки файлов (на файловый сервер)
    static class FileUploadProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            
            try {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                System.out.println("📤 Прокси загрузки файла, Content-Type: " + contentType);
                
                URL url = new URL(FILE_SERVER_URL + "/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", contentType);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                
                // Копируем тело запроса
                try (InputStream is = exchange.getRequestBody();
                     OutputStream os = conn.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
                
                int responseCode = conn.getResponseCode();
                System.out.println("📥 Ответ от файлового сервера: " + responseCode);
                
                // Копируем заголовки ответа
                exchange.getResponseHeaders().set("Content-Type", 
                    conn.getContentType() != null ? conn.getContentType() : "application/json");
                exchange.sendResponseHeaders(responseCode, 0);
                
                // Копируем тело ответа
                try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки файла: " + e.getMessage());
                e.printStackTrace();
                String error = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, error.length());
                exchange.getResponseBody().write(error.getBytes());
            } finally {
                exchange.close();
            }
        }
    }
    
    // Прокси для скачивания файлов
    static class FileDownloadProxyHandler implements HttpHandler {
        private final boolean forceDownload;
        
        public FileDownloadProxyHandler(boolean forceDownload) {
            this.forceDownload = forceDownload;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);
            
            System.out.println("📥 Запрос файла: " + fileId + (forceDownload ? " (скачивание)" : " (просмотр)"));
            
            try {
                String urlStr = FILE_SERVER_URL + "/file/" + fileId;
                if (forceDownload) {
                    urlStr = FILE_SERVER_URL + "/download/" + fileId;
                }
                
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    // Копируем заголовки
                    String contentType = conn.getContentType();
                    if (contentType != null) {
                        exchange.getResponseHeaders().set("Content-Type", contentType);
                    }
                    
                    String contentDisposition = conn.getHeaderField("Content-Disposition");
                    if (contentDisposition != null) {
                        exchange.getResponseHeaders().set("Content-Disposition", contentDisposition);
                    }
                    
                    exchange.sendResponseHeaders(200, 0);
                    
                    // Копируем тело
                    try (InputStream is = conn.getInputStream();
                         OutputStream os = exchange.getResponseBody()) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                } else {
                    exchange.sendResponseHeaders(responseCode, -1);
                }
                
            } catch (Exception e) {
                System.err.println("❌ Ошибка скачивания: " + e.getMessage());
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
}
