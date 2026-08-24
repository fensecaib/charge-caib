package caib.charge.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon

val CategoryIconOptions: List<Pair<String, ImageVector>> = listOf(
    "restaurant" to Icons.Rounded.Restaurant,
    "shopping_bag" to Icons.Rounded.ShoppingBag,
    "medical" to Icons.Rounded.LocalHospital,
    "home" to Icons.Rounded.Home,
    "directions_bus" to Icons.Rounded.DirectionsBus,
    "person" to Icons.Rounded.Person,
    "groups" to Icons.Rounded.Groups,
    "phone" to Icons.Rounded.PhoneAndroid,
    "work" to Icons.Rounded.BusinessCenter,
    "payments" to Icons.Rounded.Payments,
    "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
    "receipt_long" to Icons.AutoMirrored.Rounded.ReceiptLong,
    "undo" to Icons.AutoMirrored.Rounded.Undo,
    "redeem" to Icons.Rounded.Redeem,
    "wallet" to Icons.Rounded.AccountBalanceWallet,
    "more_horiz" to Icons.Rounded.MoreHoriz
)

fun categoryIcon(key: String): ImageVector = CategoryIconOptions.firstOrNull { it.first == key }?.second ?: Icons.Rounded.MoreHoriz

@Composable
fun CategoryIcon(key: String, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(categoryIcon(key), contentDescription = contentDescription, modifier = modifier)
}
