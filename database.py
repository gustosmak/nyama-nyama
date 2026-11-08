# Для разработки (SQLite)
DATABASE_URL=sqlite+pysqlite:///./vnya.db

# Для продакшена (PostgreSQL)
# DATABASE_URL=postgresql+asyncpg://user:password@localhost:5432/vnya_db

SECRET_KEY=your-very-super-secret-key-change-it-please-32chars
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
