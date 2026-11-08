# Информационная безопасность

## Лабораторная работа 1 — защищённый REST API

Java 17 и Spring Boot. 
Базы данных нет: чтение и запись выполняются в текстовые файлы в папке `data`.

## Запуск в PowerShell

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:APP_INITIAL_PASSWORD = 'My-local-password-123'
.\gradlew.bat bootRun
```

При первом запуске создаётся пользователь `admin`. 
Пароль берётся из `APP_INITIAL_PASSWORD`, в дефолтную папку `data/users.properties` сохраняется только bcrypt-хэш. На следующих запусках используется существующий файл; изменение переменной не меняет сохранённый пароль.

Адрес: `http://localhost:8080`. По умолчанию ключ JWT случайный: после перезапуска прежние токены недействительны. Для постоянного ключа можно задать `JWT_SECRET` (не менее 32 байтов UTF-8). Не публикуйте ключ и пароль. Каталог можно изменить через `APP_STORAGE_DIR`.

## Проверка API

Во втором окне PowerShell:

```powershell
# Без токена — HTTP 401
curl.exe -i http://localhost:8080/api/data

# Вход — HTTP 200 и JWT
$login = Invoke-RestMethod http://localhost:8080/auth/login -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"My-local-password-123"}'
$headers = @{ Authorization = "Bearer $($login.token)" }

# Добавление записи — HTTP 201
Invoke-RestMethod http://localhost:8080/api/data -Method Post -Headers $headers -ContentType 'application/json' -Body '{"text":"My first note"}'

# Чтение записей — HTTP 200
Invoke-RestMethod http://localhost:8080/api/data -Headers $headers
```

Методы:
- `POST /auth/login`: JSON с `username`, `password`; ответ содержит `token` и `type: Bearer`. Неверные данные — 401.
- `GET /api/data`: список записей `{id, text}`, нужен JWT.
- `POST /api/data`: JSON с `text` от 1 до 1000 символов; создаёт запись, нужен JWT. Пустые/некорректные данные — 400.

Записи сохраняются в `data/entries.properties` и остаются после перезапуска. Файлы читаются и записываются в UTF-8. Запись производится через временный файл и замену основного файла. Одновременные обращения синхронизированы внутри одного процесса. Запускайте только один экземпляр приложения для одной папки данных.

## Защита

- XSS: текст экранируется через `HtmlUtils.htmlEscape` при выдаче; ответы имеют формат JSON. Например, `<script>` возвращается как `&lt;script&gt;`.
- Аутентификация: JWT подписан HMAC-SHA256 и действует один час. Фильтр проверяет подпись, срок и имя пользователя на защищённых маршрутах. Повреждённые токены не дают доступ.
- Пароли хэшируются BCrypt со случайной солью. Пароли и хэши не возвращаются API. Первый пароль не зашит в исходники.
- Сессии не используются. CSRF отключён, поскольку авторизация идёт через заголовок Bearer, а не cookie. CORS не открывается для всех сайтов.
- Проверяются обязательные поля и длина ввода; подробности исключений не выдаются клиенту.


## Тесты и CI

```powershell
.\gradlew.bat test
.\gradlew.bat spotbugsMain
.\gradlew.bat dependencyCheckAnalyze
```

Тесты проверяют вход, неверный пароль, доступ без токена и с повреждённым токеном, неизвестного пользователя, истечение JWT, создание и чтение записей, XSS-экранирование, проверку ввода и повторное открытие файлов. Для тестов используется временная папка.

Настроен `.github/workflows/ci.yml`: при push/pull request запускаются тесты, SpotBugs (SAST) и OWASP Dependency-Check (SCA). 

Локальные отчёты: `build/reports/tests/test/index.html`, `build/reports/spotbugs/main.html`, `build/reports/dependency-check-report.html`.

