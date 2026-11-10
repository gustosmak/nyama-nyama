import sqlite3
import unittest
from pathlib import Path


DB_PATH = Path(__file__).with_name("foodai_menu.db")


class MenuDatabaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conn = sqlite3.connect(DB_PATH)
        cls.conn.row_factory = sqlite3.Row

    @classmethod
    def tearDownClass(cls):
        cls.conn.close()

    def test_tables_exist(self):
        names = {
            row["name"]
            for row in self.conn.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            )
        }
        self.assertTrue({"restaurants", "dishes", "sections"}.issubset(names))

    def test_sections_have_id_and_unique_name(self):
        columns = [row["name"] for row in self.conn.execute("PRAGMA table_info(sections)")]
        self.assertEqual(columns[:2], ["id", "name"])
        rows = self.conn.execute("SELECT id, name FROM sections").fetchall()
        self.assertGreater(len(rows), 0)
        ids = [row["id"] for row in rows]
        names = [row["name"] for row in rows]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertEqual(len(names), len(set(names)))
        for name in names:
            self.assertTrue(name and name.strip())

    def test_every_restaurant_is_referenced(self):
        restaurants = self.conn.execute("SELECT id, name FROM restaurants").fetchall()
        self.assertGreater(len(restaurants), 0)
        names = [row["name"] for row in restaurants]
        self.assertEqual(len(names), len(set(names)))
        orphan = self.conn.execute(
            """
            SELECT restaurants.id
            FROM restaurants
            LEFT JOIN dishes ON dishes.restaurant_id = restaurants.id
            WHERE dishes.id IS NULL
            """
        ).fetchall()
        self.assertEqual(orphan, [])

    def test_every_dish_points_at_restaurant_and_section(self):
        broken_restaurant = self.conn.execute(
            """
            SELECT dishes.id
            FROM dishes
            LEFT JOIN restaurants ON restaurants.id = dishes.restaurant_id
            WHERE restaurants.id IS NULL
            """
        ).fetchall()
        self.assertEqual(broken_restaurant, [])

        broken_section = self.conn.execute(
            """
            SELECT dishes.id, dishes.section, sections.name
            FROM dishes
            LEFT JOIN sections ON sections.id = dishes.section_id
            WHERE dishes.section IS NULL
               OR TRIM(dishes.section) = ''
               OR sections.id IS NULL
               OR sections.name != dishes.section
            """
        ).fetchall()
        self.assertEqual(list(broken_section), [])

    def test_section_names_match_dish_sections(self):
        from_dishes = {
            row["section"]
            for row in self.conn.execute(
                "SELECT DISTINCT section FROM dishes WHERE section IS NOT NULL AND TRIM(section) != ''"
            )
        }
        from_sections = {row["name"] for row in self.conn.execute("SELECT name FROM sections")}
        self.assertEqual(from_dishes, from_sections)

    def test_dish_values_are_usable(self):
        rows = self.conn.execute(
            "SELECT id, name, price, calories, satiety FROM dishes"
        ).fetchall()
        self.assertGreater(len(rows), 0)
        ids = [row["id"] for row in rows]
        self.assertEqual(len(ids), len(set(ids)))
        for row in rows:
            self.assertTrue(row["name"] and row["name"].strip())
            self.assertGreaterEqual(row["price"], 0)
            self.assertGreaterEqual(row["calories"], 0)
            self.assertGreaterEqual(row["satiety"], 1)
            self.assertLessEqual(row["satiety"], 100)


if __name__ == "__main__":
    unittest.main()
