package com.ead.evcharge.data.local.converter

import androidx.room.TypeConverter
import com.ead.evcharge.data.local.entity.SlotEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SlotListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromSlotList(slots: List<SlotEntity>): String {
        return gson.toJson(slots)
    }

    @TypeConverter
    fun toSlotList(json: String): List<SlotEntity> {
        val type = object : TypeToken<List<SlotEntity>>() {}.type
        return gson.fromJson(json, type)
    }
}