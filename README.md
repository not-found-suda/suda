# S14P31A404

## 필수 버전

- Node.js 24.x (`.nvmrc`)
- Java 21

## 프론트엔드 점검

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
```

## 백엔드 점검

```bash
cd backend
./gradlew spotlessCheck --no-daemon
./gradlew build -x test --no-daemon
```
