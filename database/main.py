from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from datetime import timedelta
from models import User
from schemas import User, UserCreate, UserLogin, Token, Restaurant, RestaurantCreate, Dish, DishCreate, Order, OrderCreate
from database import engine, get_db, Base
from crud import (
    get_user_by_email, create_user, create_restaurant, get_restaurant_by_osm,
    create_dish, create_order, get_user_orders
)
from auth import create_access_token, verify_password, get_current_user
from dotenv import load_dotenv
import os

load_dotenv()

# Для SQLite: создаем таблицы при старте, если их нет
if str(engine.url).startswith("sqlite"):
    Base.metadata.create_all(bind=engine)

app = FastAPI(title="Вкусная няма API")


@app.post("/auth/register", response_model=User)
def register(user: UserCreate, db: Session = Depends(get_db)):
    db_user = get_user_by_email(db, email=user.email)
    if db_user:
        raise HTTPException(status_code=400, detail="Email already registered")
    return create_user(db=db, user=user)


@app.post("/auth/token", response_model=Token)
def login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = get_user_by_email(db, email=form_data.username)
    if not user or not verify_password(form_data.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                            detail="Incorrect email or password",
                            headers={"WWW-Authenticate": "Bearer"})
    access_token_expires = timedelta(minutes=int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES")))
    access_token = create_access_token(
        data={"sub": user.email}, expires_delta=access_token_expires
    )
    user.last_login = None # SQLAlchemy сам подставит текущее время при коммите, если нужно
    db.commit()
    return {"access_token": access_token, "token_type": "bearer"}


@app.post("/restaurants/", response_model=Restaurant)
def add_restaurant(restaurant: RestaurantCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    # Проверяем, не загружен ли ресторан из OSM ранее
    existing = get_restaurant_by_osm(db, restaurant.osm_type, restaurant.osm_id)
    if existing:
        return existing
    return create_restaurant(db=db, restaurant=restaurant)


@app.post("/dishes/", response_model=Dish)
def add_dish(dish: DishCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return create_dish(db=db, dish=dish, restaurant_id=dish.restaurant_id)


@app.post("/orders/", response_model=Order)
def make_order(order: OrderCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return create_order(db=db, order=order, user_id=current_user.id)


@app.get("/orders/", response_model=list[Order])
def read_orders(skip: int = 0, limit: int = 100, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    orders = get_user_orders(db, current_user.id)
    return orders[skip : skip + limit]
