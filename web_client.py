from flask import Flask, render_template_string, request, jsonify
import requests
import os

app = Flask(__name__)
SERVER_URL = "http://localhost:5000"

HTML = '''
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Медиа чат</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: Arial; height: 100vh; background: #f0f2f5; }
        
        /* Логин */
        #login {
            display: flex; flex-direction: column; justify-content: center;
            align-items: center; height: 100vh;
            background: linear-gradient(135deg, #667eea, #764ba2);
        }
        #login h1 { color: white; margin-bottom: 30px; }
        #login input {
            width: 300px; padding: 15px; margin: 10px;
            border: none; border-radius: 25px; font-size: 16px;
        }
        #login button {
            width: 300px; padding: 15px; margin: 10px;
            background: #4CAF50; color: white; border: none;
            border-radius: 25px; font-size: 18px; cursor: pointer;
        }
        #login button:hover { background: #45a049; }
        
        /* Чат */
        #chat { display: none; height: 100vh; flex-direction: column; }
        #header {
            background: #4CAF50; color: white; padding: 15px;
            display: flex; justify-content: space-between;
        }
        #user-info span { font-size: 18px; font-weight: bold; }
        #user-info small { font-size: 12px; opacity: 0.9; }
        
        #messages {
            flex: 1; overflow-y: auto; padding: 15px;
            display: flex; flex-direction: column;
        }
        .message {
            max-width: 70%; margin: 5px; padding: 10px 15px;
            border-radius: 15px; word-wrap: break-word;
        }
        .my-message { align-self: flex-end; background: #4CAF50; color: white; }
        .other-message { align-self: flex-start; background: white; }
        .system-message { align-self: center; background: #ffd700; color: black; }
        
        /* Медиа */
        .media-container { max-width: 250px; margin: 5px 0; }
        .media-container img, .media-container video {
            max-width: 100%; max-height: 200px; border-radius: 10px; cursor: pointer;
        }
        .media-container audio { width: 100%; }
        .file-link {
            display: inline-block; padding: 8px 12px;
            background: rgba(255,255,255,0.2); border-radius: 15px;
            color: inherit; text-decoration: none;
        }
        
        /* Ввод */
        #input-area {
            background: white; padding: 10px; border-top: 1px solid #ddd;
            display: flex; gap: 10px; flex-wrap: wrap;
        }
        #message-input {
            flex: 1; min-width: 200px; padding: 12px;
            border: 1px solid #ddd; border-radius: 25px;
        }
        .media-btn {
            padding: 12px 15px; background: #f0f0f0; border: none;
            border-radius: 25px; cursor: pointer; font-size: 18px;
        }
        #send-btn {
            padding: 12px 25px; background: #4CAF50; color: white;
            border: none; border-radius: 25px; cursor: pointer;
        }
        
        /* Прогресс */
        #progress {
            display: none; padding: 10px; background: #e3f2fd;
            border-radius: 10px; margin: 5px;
        }
        
        /* Модальное окно */
        #modal {
            display: none; position: fixed; z-index: 1000;
            left: 0; top: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.9); justify-content: center; align-items: center;
        }
        #modal-img { max-width: 90%; max-height: 90%; }
        .close {
            position: absolute; top: 15px; right: 35px;
            color: white; font-size: 40px; cursor: pointer;
        }
        
        /* Панель пользователей */
        #users-panel {
            display: none; position: fixed; right: 0; top: 0;
            width: 300px; height: 100%; background: white;
            box-shadow: -2px 0 5px rgba(0,0,0,0.1); padding: 20px;
            overflow-y: auto; z-index: 100;
        }
        .user-item {
            padding: 10px; border-bottom: 1px solid #eee;
            display: flex; justify-content: space-between;
        }
        .online { color: #4CAF50; font-weight: bold; }
        .offline { color: #999; }
        
        /* Индикатор загрузки */
        #loading-indicator {
            display: none;
            text-align: center;
            padding: 10px;
            color: #666;
        }
        
        /* Статус */
        #status {
            position: fixed; top: 10px; right: 10px;
            padding: 5px 10px; border-radius: 15px; font-size: 12px;
            z-index: 1000;
        }
        .connected { background: #4CAF50; color: white; }
        .disconnected { background: #f44336; color: white; }
        
        /* Кнопка загрузки еще */
        #load-more-btn {
            display: block;
            width: 100%;
            padding: 10px;
            background: #e0e0e0;
            border: none;
            border-radius: 5px;
            cursor: pointer;
            margin-bottom: 10px;
        }
        #load-more-btn:hover {
            background: #d0d0d0;
        }
    </style>
</head>
<body>
    <div id="status" class="disconnected">🔴 Отключено</div>
    
    <div id="modal" onclick="closeModal()">
        <span class="close">&times;</span>
        <img id="modal-img">
    </div>
    
    <div id="users-panel">
        <h3>👥 Пользователи</h3>
        <div id="users-list"></div>
    </div>
    
    <!-- Логин -->
    <div id="login">
        <h1>📱 Медиа чат</h1>
        <input type="text" id="device" placeholder="Имя устройства" value="Phone">
        <input type="text" id="nickname" placeholder="Никнейм" value="User">
        <button onclick="register()">Войти</button>
        <div id="login-stats"></div>
    </div>
    
    <!-- Чат -->
    <div id="chat">
        <div id="header">
            <div id="user-info">
                <span id="display-name"></span><br>
                <small id="stats">0 сообщений</small>
            </div>
            <div>
                <button onclick="toggleUsers()">👥</button>
                <button onclick="logout()">Выйти</button>
            </div>
        </div>
        
        <div id="messages">
            <button id="load-more-btn" onclick="loadMoreHistory()">📜 Загрузить еще</button>
            <div id="loading-indicator">⏳ Загрузка истории...</div>
        </div>
        <div id="progress"></div>
        
        <div id="input-area">
            <input type="text" id="message-input" placeholder="Сообщение..." onkeypress="if(event.key=='Enter') send()">
            <button class="media-btn" onclick="selectFile('image')" title="Фото">📷</button>
            <button class="media-btn" onclick="selectFile('video')" title="Видео">🎥</button>
            <button class="media-btn" onclick="selectFile('audio')" title="Аудио">🎵</button>
            <button class="media-btn" onclick="selectFile('file')" title="Файл">📎</button>
            <button id="send-btn" onclick="send()">→</button>
        </div>
    </div>
    
    <input type="file" id="file-input" style="display:none">

    <script>
        var device = '';
        var nickname = '';
        var msgCount = 0;
        var fileCount = 0;
        var updateTimer = null;
        var usersTimer = null;
        var lastMessageId = null;
        var allMessages = [];
        var historyLoaded = false;
        var historyOffset = 0;
        var historyLimit = 50;
        
        // Регистрация
        function register() {
            device = document.getElementById('device').value.trim();
            nickname = document.getElementById('nickname').value.trim();
            if (nickname === '') {
                nickname = device;
            }
            
            if (device === '') {
                alert('Введите имя устройства');
                return;
            }
            
            fetch('/api/register', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({device_name: device, nickname: nickname})
            })
            .then(function(response) { return response.json(); })
            .then(function(data) {
                if (data.status === 'ok') {
                    document.getElementById('login').style.display = 'none';
                    document.getElementById('chat').style.display = 'flex';
                    document.getElementById('display-name').innerText = nickname;
                    
                    updateStatus(true);
                    
                    // Сбрасываем все
                    allMessages = [];
                    historyLoaded = false;
                    historyOffset = 0;
                    
                    // Загружаем историю
                    loadHistory();
                    
                    // Запускаем получение новых сообщений (КАЖДЫЕ 2 СЕКУНДЫ)
                    updateTimer = setInterval(getNewMessages, 2000);
                    usersTimer = setInterval(loadUsers, 5000);
                    loadUsers();
                } else {
                    alert('Ошибка: ' + JSON.stringify(data));
                }
            });
        }
        
        // Статус подключения
        function updateStatus(connected) {
            var status = document.getElementById('status');
            if (connected) {
                status.className = 'connected';
                status.innerHTML = '🟢 Подключено';
            } else {
                status.className = 'disconnected';
                status.innerHTML = '🔴 Отключено';
            }
        }
        
        // Загрузка пользователей
        function loadUsers() {
            fetch('/api/users')
                .then(function(response) { return response.json(); })
                .then(function(users) {
                    var list = document.getElementById('users-list');
                    list.innerHTML = '';
                    
                    var online = 0;
                    for (var i = 0; i < users.length; i++) {
                        var u = users[i];
                        if (u.online) {
                            online = online + 1;
                        }
                        var statusClass = u.online ? 'online' : 'offline';
                        var statusIcon = u.online ? '🟢' : '⚪';
                        list.innerHTML = list.innerHTML + 
                            '<div class="user-item">' +
                            '<span class="' + statusClass + '">' +
                            statusIcon + ' ' + u.nickname + '</span>' +
                            '<small>' + (u.messages || 0) + ' msgs</small>' +
                            '</div>';
                    }
                    
                    document.getElementById('login-stats').innerHTML = 
                        'Всего: ' + users.length + ' | Онлайн: ' + online;
                });
        }
        
        // Панель пользователей
        function toggleUsers() {
            var panel = document.getElementById('users-panel');
            if (panel.style.display === 'none' || panel.style.display === '') {
                panel.style.display = 'block';
                loadUsers();
            } else {
                panel.style.display = 'none';
            }
        }
        
        // Выход
        function logout() {
            clearInterval(updateTimer);
            clearInterval(usersTimer);
            
            fetch('/api/disconnect', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({device_name: device})
            });
            
            document.getElementById('login').style.display = 'flex';
            document.getElementById('chat').style.display = 'none';
            document.getElementById('users-panel').style.display = 'none';
            document.getElementById('messages').innerHTML = '<button id="load-more-btn" onclick="loadMoreHistory()">📜 Загрузить еще</button><div id="loading-indicator">⏳ Загрузка истории...</div>';
            msgCount = 0;
            fileCount = 0;
            allMessages = [];
        }
        
        // ЗАГРУЗКА ИСТОРИИ (СТАРЫЕ СООБЩЕНИЯ)
        function loadHistory() {
            var loading = document.getElementById('loading-indicator');
            loading.style.display = 'block';
            loading.innerHTML = '⏳ Загрузка истории...';
            
            fetch('/api/history/' + encodeURIComponent(device) + '?limit=' + historyLimit + '&offset=' + historyOffset)
                .then(function(response) { return response.json(); })
                .then(function(messages) {
                    if (messages.length > 0) {
                        // Добавляем в начало allMessages (старые сообщения)
                        allMessages = messages.concat(allMessages);
                        
                        // Перерисовываем ВСЕ сообщения
                        renderAllMessages();
                        
                        historyOffset = historyOffset + messages.length;
                        
                        if (messages.length < historyLimit) {
                            // Больше нет истории
                            document.getElementById('load-more-btn').style.display = 'none';
                        } else {
                            document.getElementById('load-more-btn').style.display = 'block';
                        }
                    } else {
                        document.getElementById('load-more-btn').style.display = 'none';
                    }
                    
                    loading.style.display = 'none';
                    historyLoaded = true;
                })
                .catch(function(error) {
                    console.error('Ошибка загрузки истории:', error);
                    addSystemMessage('❌ Ошибка загрузки истории');
                    loading.style.display = 'none';
                });
        }
        
        // Загрузить еще истории
        function loadMoreHistory() {
            historyOffset = allMessages.length;
            loadHistory();
        }
        
        // ПОЛУЧЕНИЕ НОВЫХ СООБЩЕНИЙ (раз в 2 секунды)
        function getNewMessages() {
            fetch('/api/receive?device=' + encodeURIComponent(device))
                .then(function(response) { return response.json(); })
                .then(function(messages) {
                    if (messages.length > 0) {
                        updateStatus(true);
                        
                        // Добавляем новые сообщения
                        var newMessages = [];
                        
                        for (var i = 0; i < messages.length; i++) {
                            var msg = messages[i];
                            
                            // Проверяем, нет ли уже такого сообщения
                            var exists = false;
                            for (var j = 0; j < allMessages.length; j++) {
                                if (allMessages[j].id && msg.id && allMessages[j].id === msg.id) {
                                    exists = true;
                                    break;
                                }
                                // Сравниваем по времени и тексту (грубо)
                                if (allMessages[j].sender === msg.sender && 
                                    allMessages[j].text === msg.text && 
                                    allMessages[j].time === msg.time) {
                                    exists = true;
                                    break;
                                }
                            }
                            
                            if (!exists) {
                                newMessages.push(msg);
                                allMessages.push(msg);
                                
                                // Обновляем счетчик
                                if (msg.sender === device) {
                                    msgCount = msgCount + 1;
                                    if (msg.file_id) {
                                        fileCount = fileCount + 1;
                                    }
                                }
                            }
                        }
                        
                        if (newMessages.length > 0) {
                            // Перерисовываем все сообщения
                            renderAllMessages();
                            
                            document.getElementById('stats').innerText = msgCount + ' сообщений, ' + fileCount + ' файлов';
                            
                            // Прокручиваем вниз
                            window.scrollTo(0, document.body.scrollHeight);
                        }
                    }
                })
                .catch(function() { updateStatus(false); });
        }
        
        // Отрисовка всех сообщений
        function renderAllMessages() {
            var messagesDiv = document.getElementById('messages');
            
            // Сохраняем кнопку загрузки
            var loadMoreBtn = document.getElementById('load-more-btn');
            var loadingIndicator = document.getElementById('loading-indicator');
            
            messagesDiv.innerHTML = '';
            messagesDiv.appendChild(loadMoreBtn);
            messagesDiv.appendChild(loadingIndicator);
            
            // Сортируем по времени (старые сверху)
            allMessages.sort(function(a, b) {
                if (a.time < b.time) return -1;
                if (a.time > b.time) return 1;
                return 0;
            });
            
            // Отрисовываем
            for (var i = 0; i < allMessages.length; i++) {
                renderMessage(allMessages[i]);
            }
        }
        
        // Отрисовка одного сообщения
        function renderMessage(msg) {
            var messagesDiv = document.getElementById('messages');
            var div = document.createElement('div');
            
            if (msg.sender === device) {
                div.className = 'message my-message';
            } else {
                div.className = 'message other-message';
            }
            
            var content = '<b>' + msg.sender + ':</b><br>';
            if (msg.text && msg.text.indexOf('📎') !== 0) {
                content = content + msg.text;
            }
            
            if (msg.file_id) {
                content = content + '<div class="media-container">';
                if (msg.file_type === 'image') {
                    content = content + '<img src="/api/file/' + msg.file_id + '" onclick="openModal(this.src)">';
                } else if (msg.file_type === 'video') {
                    content = content + '<video controls><source src="/api/file/' + msg.file_id + '"></video>';
                } else if (msg.file_type === 'audio') {
                    content = content + '<audio controls src="/api/file/' + msg.file_id + '"></audio>';
                } else {
                    content = content + '<a href="/api/download/' + msg.file_id + '" class="file-link" download>' +
                              '📎 Скачать ' + (msg.file_name || 'файл') + '</a>';
                }
                content = content + '</div>';
            }
            
            // Форматируем время
            var timeStr = '';
            if (msg.time) {
                if (msg.time.indexOf('T') > 0) {
                    var date = new Date(msg.time);
                    var hours = date.getHours();
                    var minutes = date.getMinutes();
                    var seconds = date.getSeconds();
                    if (hours < 10) hours = '0' + hours;
                    if (minutes < 10) minutes = '0' + minutes;
                    if (seconds < 10) seconds = '0' + seconds;
                    timeStr = hours + ':' + minutes + ':' + seconds;
                } else {
                    timeStr = msg.time;
                }
            }
            
            if (timeStr) {
                content = content + '<br><small>' + timeStr + '</small>';
            }
            
            div.innerHTML = content;
            messagesDiv.appendChild(div);
        }
        
        // Добавление системного сообщения
        function addSystemMessage(text) {
            var div = document.createElement('div');
            div.className = 'message system-message';
            div.innerHTML = text;
            document.getElementById('messages').appendChild(div);
        }
        
        // Отправка текста
        function send() {
            var text = document.getElementById('message-input').value.trim();
            if (text === '') return;
            
            fetch('/api/send', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({sender: device, text: text})
            })
            .then(function(response) { return response.json(); })
            .then(function() {
                document.getElementById('message-input').value = '';
                // Не добавляем сразу - дождемся подтверждения от сервера через receive
            });
        }
        
        // Выбор файла
        function selectFile(type) {
            var input = document.getElementById('file-input');
            if (type === 'image') {
                input.accept = 'image/*';
            } else if (type === 'video') {
                input.accept = 'video/*';
            } else if (type === 'audio') {
                input.accept = 'audio/*';
            } else {
                input.accept = '*/*';
            }
            input.click();
        }
        
        // Загрузка файла
        document.getElementById('file-input').onchange = function(e) {
            if (!this.files[0]) return;
            
            var file = this.files[0];
            var progress = document.getElementById('progress');
            progress.style.display = 'block';
            progress.innerHTML = '⏫ Загрузка: ' + file.name + '...';
            
            var formData = new FormData();
            formData.append('file', file);
            formData.append('sender', device);
            
            fetch('/api/upload', {method: 'POST', body: formData})
                .then(function(response) { return response.json(); })
                .then(function(data) {
                    if (data.status === 'ok') {
                        return fetch('/api/send', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/json'},
                            body: JSON.stringify({
                                sender: device,
                                text: '📎 ' + data.filename,
                                file_id: data.file_id,
                                file_name: data.filename,
                                file_type: data.file_type
                            })
                        });
                    }
                })
                .then(function() {
                    progress.style.display = 'none';
                })
                .catch(function(error) {
                    progress.innerHTML = '❌ Ошибка: ' + error;
                    setTimeout(function() { progress.style.display = 'none'; }, 3000);
                });
        };
        
        // Модальное окно
        function openModal(src) {
            document.getElementById('modal').style.display = 'flex';
            document.getElementById('modal-img').src = src;
        }
        
        function closeModal() {
            document.getElementById('modal').style.display = 'none';
        }
        
        // Закрытие по ESC
        document.onkeydown = function(e) {
            if (e.key === 'Escape') {
                closeModal();
            }
        };
    </script>
</body>
</html>
'''

# ========== API КЛИЕНТА ==========
@app.route('/')
def index():
    return render_template_string(HTML)

@app.route('/api/register', methods=['POST'])
def api_register():
    try:
        r = requests.post(SERVER_URL + '/register', json=request.json, timeout=5)
        return jsonify(r.json()), r.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/users')
def api_users():
    try:
        r = requests.get(SERVER_URL + '/users', timeout=5)
        return jsonify(r.json()), r.status_code
    except:
        return jsonify([])

@app.route('/api/disconnect', methods=['POST'])
def api_disconnect():
    try:
        r = requests.post(SERVER_URL + '/disconnect', json=request.json, timeout=5)
        return jsonify(r.json()), r.status_code
    except:
        return jsonify({"error": "Ошибка"}), 500

@app.route('/api/send', methods=['POST'])
def api_send():
    try:
        r = requests.post(SERVER_URL + '/send', json=request.json, timeout=5)
        return jsonify(r.json()), r.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/receive')
def api_receive():
    try:
        device = request.args.get('device', '')
        r = requests.get(SERVER_URL + '/receive?device=' + device, timeout=5)
        return jsonify(r.json()), r.status_code
    except:
        return jsonify([])

@app.route('/api/history/<device_name>')
def api_history(device_name):
    """Получение истории с сервера"""
    try:
        limit = request.args.get('limit', 50)
        offset = request.args.get('offset', 0)
        r = requests.get(SERVER_URL + '/history/' + device_name + '?limit=' + limit + '&offset=' + offset, timeout=10)
        return jsonify(r.json()), r.status_code
    except Exception as e:
        print("Ошибка получения истории: " + str(e))
        return jsonify([])

@app.route('/api/upload', methods=['POST'])
def api_upload():
    try:
        files = {'file': (request.files['file'].filename, request.files['file'].stream)}
        data = {'sender': request.form.get('sender')}
        r = requests.post(SERVER_URL + '/upload', files=files, data=data, timeout=30)
        return jsonify(r.json()), r.status_code
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/file/<file_id>')
def api_file(file_id):
    try:
        r = requests.get(SERVER_URL + '/file/' + file_id, timeout=30)
        content_type = r.headers.get('Content-Type')
        if content_type is None:
            content_type = 'application/octet-stream'
        return r.content, r.status_code, {'Content-Type': content_type}
    except:
        return jsonify({"error": "Ошибка"}), 500

@app.route('/api/download/<file_id>')
def api_download(file_id):
    try:
        r = requests.get(SERVER_URL + '/download/' + file_id, timeout=30)
        content_type = 'application/octet-stream'
        content_disp = r.headers.get('Content-Disposition')
        if content_disp is None:
            content_disp = 'attachment; filename="' + file_id + '"'
        return r.content, r.status_code, {
            'Content-Type': content_type,
            'Content-Disposition': content_disp
        }
    except:
        return jsonify({"error": "Ошибка"}), 500

if __name__ == '__main__':
    print("="*60)
    print("📱 КЛИЕНТ ЗАПУЩЕН НА ПОРТУ 5001")
    print("🌐 Открой браузер: http://localhost:5001")
    print("📜 ИСТОРИЯ ГРУЗИТСЯ АВТОМАТИЧЕСКИ!")
    print("🔄 НОВЫЕ СООБЩЕНИЯ КАЖДЫЕ 2 СЕКУНДЫ")
    print("⬆️ СТАРЫЕ СООБЩЕНИЯ СВЕРХУ")
    print("⬇️ НОВЫЕ СООБЩЕНИЯ СНИЗУ")
    print("="*60)
    app.run(host='0.0.0.0', port=5001, debug=True, threaded=True)
