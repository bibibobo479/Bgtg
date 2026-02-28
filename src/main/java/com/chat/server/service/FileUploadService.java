package com.chat.server.service;

import com.chat.server.db.DatabaseManager;
import com.chat.server.model.FileInfo;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileUploadService {
    private static final String UPLOAD_FOLDER = "uploads";
    private final Map<String, FileInfo> fileStorage = new ConcurrentHashMap<>();
    private final DatabaseManager dbManager;
    
    public FileUploadService() {
        this.dbManager = DatabaseManager.getInstance();
        createUploadFolder();
    }
    
    private void createUploadFolder() {
        File folder = new File(UPLOAD_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }
    
    // Определение типа файла
    public String getFileType(String filename) {
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
    
    // Загрузка файла
    public FileInfo uploadFile(byte[] fileData, String originalFilename, String sender) {
        try {
            String fileId = UUID.randomUUID().toString();
            String safeFilename = fileId + "_" + sanitizeFilename(originalFilename);
            String filepath = UPLOAD_FOLDER + File.separator + safeFilename;
            
            // Сохраняем файл
            Path path = Paths.get(filepath);
            Files.write(path, fileData);
            
            String fileType = getFileType(originalFilename);
            long fileSize = fileData.length;
            
            // Генерируем blurhash для изображений (упрощенная версия)
            String blurhash = null;
            if ("image".equals(fileType)) {
                blurhash = generateBlurhash(filepath);
            }
            
            // Создаем объект FileInfo
            FileInfo fileInfo = new FileInfo(originalFilename, sender, fileType, fileSize, filepath, blurhash);
            fileInfo.setFileId(fileId);
            
            // Сохраняем в памяти
            fileStorage.put(fileId, fileInfo);
            
            // Сохраняем в БД
            dbManager.saveFile(fileInfo);
            
            System.out.println("📁 Загружен: " + originalFilename + " (" + fileType + ")");
            
            return fileInfo;
            
        } catch (IOException e) {
            System.err.println("❌ Ошибка загрузки файла: " + e.getMessage());
            return null;
        }
    }
    
    // Санитизация имени файла
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }
    
    // Упрощенная генерация blurhash (заглушка)
    private String generateBlurhash(String filepath) {
        // Здесь должна быть реальная реализация генерации blurhash
        // Для примера возвращаем заглушку
        return "BLURHASH_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    // Получение файла по ID
    public FileInfo getFile(String fileId) {
        FileInfo file = fileStorage.get(fileId);
        if (file == null) {
            file = dbManager.getFileById(fileId);
        }
        return file;
    }
    
    // Чтение файла в байты
    public byte[] readFile(String filepath) throws IOException {
        Path path = Paths.get(filepath);
        return Files.readAllBytes(path);
    }
} 
