from sqlalchemy.orm import Session
from models import User, Restaurant, Dish, Order
from schemas import UserCreate, RestaurantCreate, DishCreate, OrderCreate
from auth import get_password_hash


def get_user_by_email(db: Session, email: str):
    return db.query(User).filter(User.email == email).first()


def create_user(db: Session, user: UserCreate):
    hashed_password = get_password_hash(user.password)
    db_user = User(email=user.email, password_hash=hashed_password)
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user


def create_restaurant(db: Session, restaurant: RestaurantCreate):
    db_restaurant = Restaurant(**restaurant.dict())
    db.add(db_restaurant)
    db.commit()
    db.refresh(db_restaurant)
    return db_restaurant


def get_restaurant_by_osm(db: Session, osm_type: str, osm_id: str):
    return db.query(Restaurant).filter(Restaurant.osm_type == osm_type, Restaurant.osm_id == osm_id).first()


def create_dish(db: Session, dish: DishCreate, restaurant_id: int):
    db_dish = Dish(**dish.dict(), restaurant_id=restaurant_id)
    db.add(db_dish)
    db.commit()
    db.refresh(db_dish)
    return db_dish


def create_order(db: Session, order: OrderCreate, user_id: int):
    db_order = Order(**order.dict(), user_id=user_id)
    db.add(db_order)
    db.commit()
    db.refresh(db_order)
    return db_order


def get_user_orders(db: Session, user_id: int):
    return db.query(Order).filter(Order.user_id == user_id).order_by(Order.created_at.desc()).all()
