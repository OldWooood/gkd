package li.songe.gkd.priv

import androidx.annotation.Keep

@Keep
class UserService : IUserService.Stub() {
    override fun destroy() = Unit
}
