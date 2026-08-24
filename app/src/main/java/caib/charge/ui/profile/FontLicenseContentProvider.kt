package caib.charge.ui.profile

import android.content.Context

@Suppress("UNUSED_PARAMETER")
internal fun loadFontLicenseContent(context: Context): FontLicenseContent =
    FontLicenseContent(notice = "使用系统默认字体，无额外字体许可")
