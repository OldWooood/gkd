package android.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

import java.util.List;

public interface IActivityManager extends IInterface {
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, int flags);

    @RequiresApi(Build.VERSION_CODES.P)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);
}
