# agents/preference.py

def build_profile(db, user_id: int) -> dict | None:
    """
    Заглушка для построения профиля пользователя.
    Пока базы данных нет, просто возвращаем None.
    """
    if db is None:
        return None

    # Тут в будущем будет логика запроса к БД
    return None