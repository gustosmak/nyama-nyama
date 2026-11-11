import sqlite3
from pathlib import Path

USERS_DB = Path(__file__).resolve().parents[1] / "databases" / "users.db"


def init_users():
    USERS_DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(USERS_DB)
    cursor = conn.cursor()
    cursor.executescript(
        """
        CREATE TABLE IF NOT EXISTS user_info (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            login TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS user_likes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            restaurant_id INTEGER NOT NULL,
            dish_id INTEGER NOT NULL,
            UNIQUE (user_id, restaurant_id, dish_id),
            FOREIGN KEY (user_id) REFERENCES user_info(id)
        );

        CREATE TABLE IF NOT EXISTS user_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            restaurant_id INTEGER NOT NULL,
            dish_id INTEGER NOT NULL,
            UNIQUE (user_id, restaurant_id, dish_id),
            FOREIGN KEY (user_id) REFERENCES user_info(id)
        );

        CREATE TABLE IF NOT EXISTS user_dislikes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            restaurant_id INTEGER NOT NULL,
            dish_id INTEGER NOT NULL,
            UNIQUE (user_id, restaurant_id, dish_id),
            FOREIGN KEY (user_id) REFERENCES user_info(id)
        );
        """
    )
    conn.commit()
    conn.close()
    print("users.db created")


if __name__ == "__main__":
    init_users()
