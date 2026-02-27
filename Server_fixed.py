from flask import Flask, request, jsonify, send_file
from collections import defaultdict
import threading
import time
import os
import uuid
import sqlite3
import datetime
from werkzeug.utils import secure_filename

app = Flask(__name__)

# Конфигурация
UPLOAD_FOLDER = 'uploads'
ALLOWED_EXTENSIONS = {
    'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp',
    'mp4', 'avi', 'mov', 'mkv', 'webm',
    'mp3', 'wav', 'ogg', 'm4a', 'aac',
    'txt', 'pdf', 'doc', 'docx', 'zip', 'rar'
}

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

message_queues = defaultdict(list)
file_storage = {}
lock = threading.Lock()

# ========== ИНИЦИАЛИЗАЦИЯ БАЗЫ ДАННЫХ ==========
def init_database():
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    # Пользователи
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nickname TEXT,
            device_name TEXT UNIQUE,
            ip_address TEXT,
            first_seen TIMESTAMP,
            last_seen TIMESTAMP,
            total_messages INTEGER DEFAULT 0,
            total_files INTEGER DEFAULT 0
        )
    ''')
    
    # Сообщения (ИСТОРИЯ!)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id TEXT UNIQUE,
            sender TEXT,
            recipient TEXT,
            text TEXT,
            file_id TEXT,
            file_name TEXT,
            file_type TEXT,
            timestamp TIMESTAMP
        )
    ''')
    
    # Файлы
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_id TEXT UNIQUE,
            filename TEXT,
            sender TEXT,
            file_type TEXT,
            file_size INTEGER,
            saved_path TEXT,
            timestamp TIMESTAMP
        )
    ''')
    
    # Подключения
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS connections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            ip_address TEXT,
            connected_at TIMESTAMP,
            disconnected_at TIMESTAMP
        )
    ''')
    
    conn.commit()
    conn.close()
    print("📊 База данных инициализирована (с историей сообщений)")

init_database()

# ========== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ==========
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def get_file_type(filename):
    if '.' in filename:
        ext = filename.rsplit('.', 1)[1].lower()
    else:
        ext = ''
    
    if ext in {'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'}:
        return 'image'
    elif ext in {'mp4', 'avi', 'mov', 'mkv', 'webm'}:
        return 'video'
    elif ext in {'mp3', 'wav', 'ogg', 'm4a', 'aac'}:
        return 'audio'
    else:
        return 'file'

def save_message_to_db(sender, recipient, text, file_id, file_name, file_type):
    """Сохраняет сообщение в историю"""
    try:
        conn = sqlite3.connect('chat.db')
        cursor = conn.cursor()
        message_id = str(uuid.uuid4())
        cursor.execute('''
            INSERT INTO messages (message_id, sender, recipient, text, file_id, file_name, file_type, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', (message_id, sender, recipient, text, file_id, file_name, file_type, datetime.datetime.now()))
        conn.commit()
        conn.close()
        return message_id
    except Exception as e:
        print("❌ Ошибка сохранения сообщения: " + str(e))
        return None

def save_file_to_db(file_id, filename, sender, file_type, file_size, saved_path):
    """Сохраняет информацию о файле"""
    try:
        conn = sqlite3.connect('chat.db')
        cursor = conn.cursor()
        cursor.execute('''
            INSERT INTO files (file_id, filename, sender, file_type, file_size, saved_path, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (file_id, filename, sender, file_type, file_size, saved_path, datetime.datetime.now()))
        conn.commit()
        conn.close()
        return True
    except Exception as e:
        print("❌ Ошибка сохранения файла: " + str(e))
        return False

# ========== ПОЛЬЗОВАТЕЛИ ==========
@app.route('/register', methods=['POST'])
def register():
    data = request.json
    device_name = data.get('device_name')
    nickname = data.get('nickname', device_name)
    
    if not device_name:
        return jsonify({"error": "Не указано устройство"}), 400
    
    ip = request.remote_addr
    
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    try:
        cursor.execute('SELECT id FROM users WHERE device_name = ?', (device_name,))
        existing = cursor.fetchone()
        
        if existing:
            cursor.execute('''
                UPDATE users 
                SET nickname = ?, ip_address = ?, last_seen = ?
                WHERE device_name = ?
            ''', (nickname, ip, datetime.datetime.now(), device_name))
            user_id = existing[0]
            message = "Пользователь обновлен"
            print("🔄 Обновлен: " + str(device_name))
        else:
            cursor.execute('''
                INSERT INTO users (nickname, device_name, ip_address, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?)
            ''', (nickname, device_name, ip, datetime.datetime.now(), datetime.datetime.now()))
            user_id = cursor.lastrowid
            message = "Пользователь создан"
            print("✅ Новый: " + str(device_name))
        
        cursor.execute('''
            INSERT INTO connections (user_id, ip_address, connected_at)
            VALUES (?, ?, ?)
        ''', (user_id, ip, datetime.datetime.now()))
        
        conn.commit()
        
        with lock:
            if device_name not in message_queues:
                message_queues[device_name] = []
        
        return jsonify({
            "status": "ok",
            "message": message,
            "device_name": device_name,
            "nickname": nickname
        })
        
    except Exception as e:
        print("❌ Ошибка: " + str(e))
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

@app.route('/users', methods=['GET'])
def get_users():
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    cursor.execute('''
        SELECT nickname, device_name, ip_address, last_seen, total_messages, total_files
        FROM users ORDER BY last_seen DESC
    ''')
    
    users = []
    for row in cursor.fetchall():
        online = False
        if row[3]:
            try:
                last_str = row[3].replace(' ', 'T')
                last = datetime.datetime.fromisoformat(last_str)
                now = datetime.datetime.now()
                diff = now - last
                if diff.seconds < 120:
                    online = True
            except:
                pass
        
        users.append({
            "nickname": row[0],
            "device_name": row[1],
            "ip": row[2],
            "last_seen": row[3],
            "online": online,
            "messages": row[4],
            "files": row[5]
        })
    
    conn.close()
    return jsonify(users)

@app.route('/user/<device_name>', methods=['GET'])
def get_user(device_name):
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    cursor.execute('''
        SELECT nickname, device_name, ip_address, first_seen, last_seen, total_messages, total_files
        FROM users WHERE device_name = ?
    ''', (device_name,))
    
    user = cursor.fetchone()
    if not user:
        return jsonify({"error": "Не найден"}), 404
    
    cursor.execute('''
        SELECT ip_address, connected_at, disconnected_at
        FROM connections WHERE user_id = (SELECT id FROM users WHERE device_name = ?)
        ORDER BY connected_at DESC LIMIT 10
    ''', (device_name,))
    
    connections = []
    for row in cursor.fetchall():
        connections.append({
            "ip": row[0],
            "connected": row[1],
            "disconnected": row[2]
        })
    
    conn.close()
    
    return jsonify({
        "nickname": user[0],
        "device": user[1],
        "ip": user[2],
        "first_seen": user[3],
        "last_seen": user[4],
        "messages": user[5],
        "files": user[6],
        "connections": connections
    })

@app.route('/disconnect', methods=['POST'])
def disconnect():
    data = request.json
    device_name = data.get('device_name')
    
    if not device_name:
        return jsonify({"error": "Не указано устройство"}), 400
    
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    cursor.execute('''
        UPDATE connections 
        SET disconnected_at = ? 
        WHERE user_id = (SELECT id FROM users WHERE device_name = ?)
        AND disconnected_at IS NULL
    ''', (datetime.datetime.now(), device_name))
    
    conn.commit()
    conn.close()
    
    print("👋 Отключился: " + str(device_name))
    
    return jsonify({"status": "ok", "message": "Отключен"})

# ========== ИСТОРИЯ СООБЩЕНИЙ ==========
@app.route('/history/<device_name>', methods=['GET'])
def get_history(device_name):
    """Получить историю сообщений для устройства"""
    limit = request.args.get('limit', 100)
    offset = request.args.get('offset', 0)
    try:
        limit = int(limit)
        offset = int(offset)
    except:
        limit = 100
        offset = 0
    
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    # Получаем сообщения где device_name участвует (отправитель, получатель, или всем)
    cursor.execute('''
        SELECT sender, recipient, text, file_id, file_name, file_type, timestamp
        FROM messages 
        WHERE sender = ? OR recipient = ? OR recipient = 'all'
        ORDER BY timestamp DESC LIMIT ? OFFSET ?
    ''', (device_name, device_name, limit, offset))
    
    messages = []
    for row in cursor.fetchall():
        messages.append({
            "sender": row[0],
            "recipient": row[1],
            "text": row[2],
            "file_id": row[3],
            "file_name": row[4],
            "file_type": row[5],
            "time": row[6]
        })
    
    conn.close()
    return jsonify(messages)

@app.route('/history/all', methods=['GET'])
def get_all_history():
    """Получить всю историю (для админа)"""
    limit = request.args.get('limit', 500)
    try:
        limit = int(limit)
    except:
        limit = 500
    
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    
    cursor.execute('''
        SELECT sender, recipient, text, file_id, file_name, file_type, timestamp
        FROM messages 
        ORDER BY timestamp DESC LIMIT ?
    ''', (limit,))
    
    messages = []
    for row in cursor.fetchall():
        messages.append({
            "sender": row[0],
            "recipient": row[1],
            "text": row[2],
            "file_id": row[3],
            "file_name": row[4],
            "file_type": row[5],
            "time": row[6]
        })
    
    conn.close()
    return jsonify(messages)

# ========== СООБЩЕНИЯ ==========
@app.route('/send', methods=['POST'])
def send_message():
    data = request.json
    
    sender = data.get('sender')
    text = data.get('text', '')
    recipient = data.get('recipient', 'all')
    file_id = data.get('file_id')
    file_name = data.get('file_name')
    file_type = data.get('file_type', 'text')
    
    if not sender:
        return jsonify({"error": "Нет отправителя"}), 400
    
    # СОХРАНЯЕМ В ИСТОРИЮ!
    message_id = save_message_to_db(sender, recipient, text, file_id, file_name, file_type)
    
    # Обновляем статистику
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    cursor.execute('UPDATE users SET total_messages = total_messages + 1 WHERE device_name = ?', (sender,))
    if file_id:
        cursor.execute('UPDATE users SET total_files = total_files + 1 WHERE device_name = ?', (sender,))
    conn.commit()
    conn.close()
    
    # Создаем сообщение для рассылки
    message = {
        'sender': sender,
        'text': text,
        'time': datetime.datetime.now().strftime('%H:%M:%S'),
        'type': file_type,
        'message_id': message_id
    }
    
    if file_id:
        message['file_id'] = file_id
        message['file_name'] = file_name
        message['file_type'] = file_type
    
    # Рассылаем ВСЕМ, ВКЛЮЧАЯ ОТПРАВИТЕЛЯ!
    with lock:
        if recipient == 'all':
            for device in message_queues:
                # ТЕПЕРЬ ОТПРАВЛЯЕМ И ОТПРАВИТЕЛЮ ТОЖЕ!
                message_queues[device].append(message.copy())
            print("📤 " + str(sender) + " -> всем (включая отправителя): " + str(text[:30]))
        else:
            if recipient in message_queues:
                message_queues[recipient].append(message)
                # И отправителю тоже отправляем
                if sender in message_queues:
                    message_queues[sender].append(message.copy())
                print("📤 " + str(sender) + " -> " + str(recipient) + ": " + str(text[:30]))
    
    return jsonify({"status": "ok", "message_id": message_id})

@app.route('/receive', methods=['GET'])
def receive_messages():
    device = request.args.get('device')
    
    if not device:
        return jsonify([])
    
    with lock:
        if device not in message_queues:
            message_queues[device] = []
            return jsonify([])
        
        messages = message_queues[device][:]
        message_queues[device].clear()
    
    return jsonify(messages)

# ========== ФАЙЛЫ ==========
@app.route('/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        return jsonify({"error": "Нет файла"}), 400
    
    file = request.files['file']
    sender = request.form.get('sender', 'unknown')
    
    if file.filename == '':
        return jsonify({"error": "Нет файла"}), 400
    
    if not allowed_file(file.filename):
        return jsonify({"error": "Недопустимый тип файла"}), 400
    
    file_id = str(uuid.uuid4())
    filename = secure_filename(file_id + "_" + file.filename)
    filepath = os.path.join(UPLOAD_FOLDER, filename)
    
    file.save(filepath)
    
    file_type = get_file_type(file.filename)
    file_size = os.path.getsize(filepath)
    
    with lock:
        file_storage[file_id] = {
            'filename': file.filename,
            'path': filepath,
            'sender': sender,
            'type': file_type,
            'size': file_size
        }
    
    # СОХРАНЯЕМ ИНФОРМАЦИЮ О ФАЙЛЕ В БД!
    save_file_to_db(file_id, file.filename, sender, file_type, file_size, filepath)
    
    print("📁 Загружен: " + str(file.filename) + " (" + str(file_type) + ")")
    
    return jsonify({
        "status": "ok",
        "file_id": file_id,
        "filename": file.filename,
        "file_type": file_type,
        "size": file_size
    })

@app.route('/file/<file_id>')
def get_file(file_id):
    with lock:
        if file_id in file_storage:
            return send_file(file_storage[file_id]['path'])
    
    # Если нет в памяти, ищем в БД
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    cursor.execute('SELECT saved_path FROM files WHERE file_id = ?', (file_id,))
    row = cursor.fetchone()
    conn.close()
    
    if row and os.path.exists(row[0]):
        return send_file(row[0])
    
    return jsonify({"error": "Файл не найден"}), 404

@app.route('/download/<file_id>')
def download_file(file_id):
    with lock:
        if file_id in file_storage:
            info = file_storage[file_id]
            return send_file(info['path'], as_attachment=True, download_name=info['filename'])
    
    # Если нет в памяти, ищем в БД
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    cursor.execute('SELECT saved_path, filename FROM files WHERE file_id = ?', (file_id,))
    row = cursor.fetchone()
    conn.close()
    
    if row and os.path.exists(row[0]):
        return send_file(row[0], as_attachment=True, download_name=row[1])
    
    return jsonify({"error": "Файл не найден"}), 404

# ========== СТАТУС ==========
@app.route('/status')
def status():
    with lock:
        active = list(message_queues.keys())
        files_count = len(file_storage)
        
        images = 0
        videos = 0
        audio = 0
        other = 0
        
        for f in file_storage.values():
            if f['type'] == 'image':
                images = images + 1
            elif f['type'] == 'video':
                videos = videos + 1
            elif f['type'] == 'audio':
                audio = audio + 1
            else:
                other = other + 1
    
    # Статистика из БД
    conn = sqlite3.connect('chat.db')
    cursor = conn.cursor()
    cursor.execute('SELECT COUNT(*) FROM users')
    total_users = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM users WHERE last_seen > datetime("now", "-2 minutes")')
    online_users = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM messages')
    total_messages_db = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM files')
    total_files_db = cursor.fetchone()[0]
    conn.close()
    
    total_waiting = 0
    for q in message_queues.values():
        total_waiting = total_waiting + len(q)
    
    return jsonify({
        "status": "running",
        "active_devices": active,
        "total_messages_waiting": total_waiting,
        "file_statistics": {
            "total": files_count,
            "images": images,
            "videos": videos,
            "audio": audio,
            "other": other
        },
        "database_stats": {
            "total_users": total_users,
            "online_users": online_users,
            "total_messages": total_messages_db,
            "total_files": total_files_db
        }
    })

# ========== ОЧИСТКА ==========
def cleanup_old_files():
    """Удаляет файлы старше 24 часов"""
    while True:
        time.sleep(3600)
        current_time = time.time()
        with lock:
            to_delete = []
            for file_id, info in file_storage.items():
                if os.path.exists(info['path']):
                    file_time = os.path.getmtime(info['path'])
                    if current_time - file_time > 86400:  # 24 часа
                        try:
                            os.remove(info['path'])
                        except:
                            pass
                        to_delete.append(file_id)
            for file_id in to_delete:
                del file_storage[file_id]
            if to_delete:
                print("🧹 Очищено " + str(len(to_delete)) + " старых файлов")

# Запуск
if __name__ == '__main__':
    cleanup_thread = threading.Thread(target=cleanup_old_files)
    cleanup_thread.daemon = True
    cleanup_thread.start()
    
    print("="*70)
    print("🚀 СЕРВЕР ЗАПУЩЕН НА ПОРТУ 5000 (С ИСТОРИЕЙ!)")
    print("="*70)
    print("📱 Регистрация:  POST /register")
    print("👥 Пользователи: GET /users")
    print("💬 Отправка:     POST /send")
    print("📥 Получение:    GET /receive?device=NAME")
    print("📁 Загрузка:     POST /upload")
    print("🖼️ Просмотр:     GET /file/ID")
    print("📎 Скачать:      GET /download/ID")
    print("📊 Статус:       GET /status")
    print("📜 ИСТОРИЯ:      GET /history/NAME")
    print("="*70)
    print("📁 Поддерживаются: фото, видео, аудио, документы")
    print("✅ ОТПРАВИТЕЛЬ ТОЖЕ ВИДИТ СВОИ СООБЩЕНИЯ!")
    print("="*70)
    
    app.run(host='0.0.0.0', port=5000, debug=True, threaded=True)
