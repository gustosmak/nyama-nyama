from pydantic import BaseModel, EmailStr, Field
from datetime import datetime
from typing import Optional


class Token(BaseModel):
    access_token: str
    token_type: str


class TokenData(BaseModel):
    email: Optional[str] = None


class UserBase(BaseModel):
    email: EmailStr


class UserCreate(UserBase):
    password: str


class User(UserBase):
    id: int
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class RestaurantBase(BaseModel):
    osm_id: str
    osm_type: str
    name: str
    address: Optional[str] = None
    lat: float
    lon: float


class RestaurantCreate(RestaurantBase):
    pass


class Restaurant(RestaurantBase):
    id: int
    cached_at: datetime

    class Config:
        from_attributes = True


class DishBase(BaseModel):
    name: str
    price: float = Field(gt=0)
    calories: int = Field(gt=0)
    category: Optional[str] = None
    is_spicy: bool = False
    is_veg: bool = False
    description: Optional[str] = None


class DishCreate(DishBase):
    pass


class Dish(DishBase):
    id: int
    restaurant_id: int
    cached_at: datetime

    class Config:
        from_attributes = True


class OrderBase(BaseModel):
    dish_id: int
    price: float
    rating: Optional[int] = Field(None, ge=1, le=5)


class OrderCreate(OrderBase):
    pass


class Order(OrderBase):
    id: int
    user_id: int
    created_at: datetime

    class Config:
        from_attributes = True
