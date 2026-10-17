package li.songe.gkd.priv

import android.content.Context
import com.android.internal.app.IAppOpsService

class CompatAppOpsService {
    val value: IAppOpsService = IAppOpsService.Stub.asInterface(
        shizukuSystemService(Context.APP_OPS_SERVICE),
    )
}
