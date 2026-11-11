# init_db.py
import sqlite3
from pathlib import Path
from menu_data import MENU_DATABASE

MENU_DB = Path(__file__).resolve().parents[1] / "databases" / "foodai_menu.db"

def calculate_satiety(calories: int) -> int:
    """
    Формула сытности: 1000 ккал = 100 сытности.
    Максимум 100. Если калорий нет, ставим 50.
    """
    if not calories:
        return 50
    return min(100, int(calories / 10))

def init_db():
    # Подключаемся к файлу базы данных (он создастся автоматически)
    MENU_DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(MENU_DB)
    cursor = conn.cursor()

    # Удаляем старые таблицы, если они есть (чтобы не было ошибок при перезапуске)
    cursor.execute('DROP TABLE IF EXISTS dishes')
    cursor.execute('DROP TABLE IF EXISTS sections')
    cursor.execute('DROP TABLE IF EXISTS restaurants')

    # 1. Таблица ресторанов
    cursor.execute('''
        CREATE TABLE restaurants (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL
        )
    ''')

    # 2. Секции меню. Названия те же, что у блюд в поле section.
    cursor.execute('''
        CREATE TABLE sections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL
        )
    ''')

    # 3. Таблица блюд (связана с рестораном и секцией)
    cursor.execute('''
        CREATE TABLE dishes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            restaurant_id INTEGER,
            name TEXT NOT NULL,
            section TEXT,
            section_id INTEGER,
            price REAL,
            calories INTEGER,
            satiety INTEGER,
            description TEXT,
            generated INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
            FOREIGN KEY (section_id) REFERENCES sections(id)
        )
    ''')

    # 3. Заполняем базу данными из твоего словаря
    for rest_name, dishes in MENU_DATABASE.items():
        # Добавляем ресторан
        cursor.execute('INSERT INTO restaurants (name) VALUES (?)', (rest_name,))
        rest_id = cursor.lastrowid # Получаем ID только что созданного ресторана

        # Добавляем блюда этого ресторана
        for dish in dishes:
            satiety = calculate_satiety(dish.get('calories', 0))
            section_name = dish.get('section') or ''
            cursor.execute(
                'INSERT OR IGNORE INTO sections (name) VALUES (?)',
                (section_name,)
            )
            cursor.execute('SELECT id FROM sections WHERE name = ?', (section_name,))
            section_id = cursor.fetchone()[0]
            cursor.execute('''
                INSERT INTO dishes (
                    restaurant_id, name, section, section_id, price, calories, satiety, description
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                rest_id,
                dish.get('name'),
                section_name,
                section_id,
                dish.get('price'),
                dish.get('calories', 0),
                satiety,
                dish.get('description', '')
            ))

    conn.commit()
    conn.close()
    print("✅ База данных 'foodai_menu.db' успешно создана и заполнена!")

if __name__ == "__main__":
    init_db()