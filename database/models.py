from datetime import datetime
from sqlalchemy import (
    String, Boolean, Float, Text, DateTime, ForeignKey, Index, func
)
from sqlalchemy.orm import relationship, Mapped, mapped_column
from database import Base


class User(Base):
    """
    Пользователь приложения. 
    Аутентификация происходит по email, пароль хранится в виде bcrypt-хэша.
    """
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    email: Mapped[str] = mapped_column(String(120), unique=True, nullable=False, index=True)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    last_login: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    orders: Mapped[list["Order"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Restaurant(Base):
    """
    Ресторан быстрого питания. 
    Данные кэшируются из OpenStreetMap (OSM) API.
    """
    __tablename__ = "restaurants"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    osm_id: Mapped[str] = mapped_column(String, unique=True, nullable=False) -- ID из OSM
    osm_type: Mapped[str] = mapped_column(String(20), nullable=False) -- node, way или relation
    name: Mapped[str] = mapped_column(nullable=False)
    address: Mapped[str | None] = mapped_column(String)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lon: Mapped[float] = mapped_column(Float, nullable=False)
    cached_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    dishes: Mapped[list["Dish"]] = relationship(back_populates="restaurant", cascade="all, delete-orphan")

    __table_args__ = (
        Index("idx_restaurants_location", "lat", "lon"),
    )


class Dish(Base):
    """
    Блюдо из меню ресторана. 
    Данные кэшируются из OSM API (тег 'menu' или кастомные теги заведений).
    """
    __tablename__ = "dishes"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    restaurant_id: Mapped[int] = mapped_column(ForeignKey("restaurants.id", ondelete="CASCADE"), nullable=False)
    name: Mapped[str] = mapped_column(nullable=False)
    price: Mapped[float] = mapped_column(Float, nullable=False)
    calories: Mapped[int] = mapped_column(nullable=False)
    category: Mapped[str | None] = mapped_column(String)
    is_spicy: Mapped[bool] = mapped_column(default=False)
    is_veg: Mapped[bool] = mapped_column(default=False)
    description: Mapped[str | None] = mapped_column(Text)
    cached_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    restaurant: Mapped[Restaurant] = relationship(back_populates="dishes")
    orders: Mapped[list["Order"]] = relationship(back_populates="dish", cascade="all, delete-orphan")

    __table_args__ = (
        Index("idx_dishes_restaurant_id", "restaurant_id"),
    )


class Order(Base):
    """
    История заказов пользователя. 
    Связывает пользователя с конкретным блюдом.
    """
    __tablename__ = "orders"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    dish_id: Mapped[int] = mapped_column(ForeignKey("dishes.id", ondelete="CASCADE"), nullable=False)
    price: Mapped[float] = mapped_column(Float, nullable=False)
    rating: Mapped[int | None] = mapped_column(nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    user: Mapped[User] = relationship(back_populates="orders")
    dish: Mapped[Dish] = relationship(back_populates="orders")

    __table_args__ = (
        Index("idx_orders_user_created", "user_id", "created_at"),
    )
