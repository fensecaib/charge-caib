package caib.charge.domain.model

object BuiltInCategoryIds {
    const val EXPENSE_FOOD = "expense_food"
    const val EXPENSE_SHOPPING = "expense_shopping"
    const val EXPENSE_MEDICAL = "expense_medical"
    const val EXPENSE_DAILY = "expense_daily"
    const val EXPENSE_TRANSPORT = "expense_transport"
    const val EXPENSE_PERSONAL = "expense_personal"
    const val EXPENSE_SOCIAL = "expense_social"
    const val EXPENSE_COMMUNICATION = "expense_communication"
    const val EXPENSE_WORK = "expense_work"
    const val EXPENSE_OTHER = "expense_other"

    const val INCOME_SALARY = "income_salary"
    const val INCOME_GIFT = "income_gift"
    const val INCOME_INVESTMENT = "income_investment"
    const val INCOME_REIMBURSEMENT = "income_reimbursement"
    const val INCOME_REFUND = "income_refund"
    const val INCOME_RED_PACKET = "income_red_packet"
    const val INCOME_OTHER = "income_other"

    fun other(type: EntryType): String = when (type) {
        EntryType.EXPENSE -> EXPENSE_OTHER
        EntryType.INCOME -> INCOME_OTHER
    }
}
