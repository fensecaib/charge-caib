package caib.charge.data.local

import caib.charge.data.local.entity.CategoryEntity
import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.BuiltInCategoryIds
import caib.charge.domain.model.EntryType

object BuiltInCategories {
    private data class Definition(
        val id: String,
        val type: EntryType,
        val name: String,
        val icon: String,
        val color: String,
    )

    private val definitions = listOf(
        Definition(BuiltInCategoryIds.EXPENSE_FOOD, EntryType.EXPENSE, "餐饮", "restaurant", "2"),
        Definition(BuiltInCategoryIds.EXPENSE_SHOPPING, EntryType.EXPENSE, "购物", "shopping_bag", "1"),
        Definition(BuiltInCategoryIds.EXPENSE_MEDICAL, EntryType.EXPENSE, "医疗", "medical", "7"),
        Definition(BuiltInCategoryIds.EXPENSE_DAILY, EntryType.EXPENSE, "日用", "home", "6"),
        Definition(BuiltInCategoryIds.EXPENSE_TRANSPORT, EntryType.EXPENSE, "交通", "directions_bus", "0"),
        Definition(BuiltInCategoryIds.EXPENSE_PERSONAL, EntryType.EXPENSE, "个人", "person", "4"),
        Definition(BuiltInCategoryIds.EXPENSE_SOCIAL, EntryType.EXPENSE, "社交", "groups", "3"),
        Definition(BuiltInCategoryIds.EXPENSE_COMMUNICATION, EntryType.EXPENSE, "通讯", "phone", "6"),
        Definition(BuiltInCategoryIds.EXPENSE_WORK, EntryType.EXPENSE, "工作", "work", "5"),
        Definition(BuiltInCategoryIds.EXPENSE_OTHER, EntryType.EXPENSE, "其他", "more_horiz", "4"),
        Definition(BuiltInCategoryIds.INCOME_SALARY, EntryType.INCOME, "工资", "payments", "3"),
        Definition(BuiltInCategoryIds.INCOME_GIFT, EntryType.INCOME, "礼金", "wallet", "1"),
        Definition(BuiltInCategoryIds.INCOME_INVESTMENT, EntryType.INCOME, "理财", "trending_up", "6"),
        Definition(BuiltInCategoryIds.INCOME_REIMBURSEMENT, EntryType.INCOME, "报销", "receipt_long", "0"),
        Definition(BuiltInCategoryIds.INCOME_REFUND, EntryType.INCOME, "退款", "undo", "4"),
        Definition(BuiltInCategoryIds.INCOME_RED_PACKET, EntryType.INCOME, "红包", "redeem", "2"),
        Definition(BuiltInCategoryIds.INCOME_OTHER, EntryType.INCOME, "其他", "more_horiz", "5"),
    )

    fun entities(timestamp: Long = 0L): List<CategoryEntity> {
        val counters = mutableMapOf<EntryType, Int>()
        return definitions.map { definition ->
            val order = counters.getOrDefault(definition.type, 0)
            counters[definition.type] = order + 1
            CategoryEntity(
                id = definition.id,
                type = definition.type,
                name = definition.name,
                normalizedName = TextNormalizer.normalize(definition.name),
                iconKey = definition.icon,
                colorKey = definition.color,
                sortOrder = order,
                isBuiltIn = true,
                isArchived = false,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
    }
}
