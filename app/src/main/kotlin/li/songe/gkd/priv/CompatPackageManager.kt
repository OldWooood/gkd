package li.songe.gkd.priv

import android.annotation.SuppressLint
import android.app.ApplicationPackageManager
import android.content.pm.IPackageManager
import li.songe.gkd.app

class CompatPackageManager {

    val iPackageManager: IPackageManager = IPackageManager.Stub.asInterface(
        shizukuSystemService("package"),
    )

    val appPackageManager: ApplicationPackageManager = ApplicationPackageManager::class.java
        .getDeclaredConstructor(
            @SuppressLint("PrivateApi") Class.forName("android.app.ContextImpl"),
            IPackageManager::class.java,
        )
        .apply { isAccessible = true }
        .newInstance(
            app.baseContext,
            iPackageManager,
        )
}
