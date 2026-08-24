package caib.charge.data.local

import androidx.room.TypeConverter
import caib.charge.domain.model.EntrySource
import caib.charge.domain.model.EntryType

class CaibTypeConverters {
    @TypeConverter fun entryTypeToString(value: EntryType): String = value.name
    @TypeConverter fun stringToEntryType(value: String): EntryType = EntryType.valueOf(value)
    @TypeConverter fun entrySourceToString(value: EntrySource): String = value.name
    @TypeConverter fun stringToEntrySource(value: String): EntrySource = EntrySource.valueOf(value)
}
