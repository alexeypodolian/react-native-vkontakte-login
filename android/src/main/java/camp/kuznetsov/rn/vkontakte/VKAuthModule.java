package camp.kuznetsov.rn.vkontakte;


import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.vk.sdk.VKAccessToken;
import com.vk.sdk.VKAccessTokenTracker;
import com.vk.sdk.VKCallback;
import com.vk.sdk.VKSdk;
import com.vk.sdk.api.VKError;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class VKAuthModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    private static final String LOG = "VKAuthModule";

    private static final String VK_API_VERSION = "5.52";

    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
    private static final String E_NOT_INITIALIZED = "E_NOT_INITIALIZED";
    private static final String E_VKSDK_ERROR = "E_VKSDK_ERROR";
    private static final String TOKEN_INVALID = "TOKEN_INVALID";
    private static final String M_NOT_INITIALIZED = "VK SDK must be initialized first";

    private Promise loginPromise;
    private boolean isInitialized = false;

    @Override
    public void onNewIntent(Intent intent) {
    }

    public VKAuthModule(final ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        VKAccessTokenTracker vkAccessTokenTracker = new VKAccessTokenTracker() {
            @Override
            public void onVKAccessTokenChanged(VKAccessToken oldToken, VKAccessToken newToken) {
                if (newToken == null) {
                    reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit(TOKEN_INVALID, null);
                }
            }
        };
        vkAccessTokenTracker.startTracking();
        int appId = 0;
        int resId = reactContext.getResources().getIdentifier(VKSdk.SDK_APP_ID, "integer", reactContext.getPackageName());
        try {
            appId = reactContext.getResources().getInteger(resId);
        } catch (Exception e) {
        }
        Log.d(LOG, "VK AppID found in resources: " + appId);
        if (appId != 0) {
            VKSdk.customInitialize(reactContext, appId, VK_API_VERSION);
            isInitialized = true;
        }
    }

    @Override
    public String getName() {
        return "VkontakteManager";
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(TOKEN_INVALID, TOKEN_INVALID);
        return constants;
    }

    @ReactMethod
    public void initialize(final Integer appId) {
        Log.d(LOG, "Inititalizing " + appId);
        if (appId != 0) {
            VKSdk.customInitialize(getReactApplicationContext(), appId, VK_API_VERSION);
            isInitialized = true;
        } else {
            throw new JSApplicationIllegalArgumentException("VK App Id cannot be 0");
        }
    }

    @ReactMethod
    public void login(final ReadableArray scope, final Promise promise) {
        if (!isInitialized) {
            promise.reject(E_NOT_INITIALIZED, M_NOT_INITIALIZED);
            return;
        }
        Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        int scopeSize = scope.size();
        String[] scopeArray = new String[scopeSize];
        for (int i = 0; i < scopeSize; i++) {
            scopeArray[i] = scope.getString(i);
        }

        final VKAccessToken vkAccessToken = VKAccessToken.currentToken();

        boolean hasScope = false;

        try {
            hasScope = vkAccessToken != null && vkAccessToken.hasScope(scopeArray);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        if (VKSdk.isLoggedIn() && hasScope) {
            Log.d(LOG, "Already logged in with all requested scopes");
            promise.resolve(makeLoginResponse(vkAccessToken));
        } else {
            Log.d(LOG, "Requesting scopes (" + scopeSize + ") " + Arrays.toString(scopeArray));
            loginPromise = promise;
            VKSdk.login(activity, scopeArray);
        }
    }

    @ReactMethod
    public void logout(Promise promise) {
        if (!isInitialized) {
            promise.reject(E_NOT_INITIALIZED, M_NOT_INITIALIZED);
            return;
        }
        VKSdk.logout();
        promise.resolve(null);
    }

    @ReactMethod
    public void isLoggedIn(Promise promise) {
        if (isInitialized) {
            promise.resolve(VKSdk.isLoggedIn());
        } else {
            promise.reject(E_NOT_INITIALIZED, M_NOT_INITIALIZED);
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        VKSdk.onActivityResult(requestCode, resultCode, data, new VKCallback<VKAccessToken>() {
            @Override
            public void onResult(VKAccessToken res) {
                if (loginPromise != null) {
                    loginPromise.resolve(makeLoginResponse(res));
                    loginPromise = null;
                }
            }

            @Override
            public void onError(VKError error) {
                if (loginPromise != null) {
                    loginPromise.reject(E_VKSDK_ERROR, error.toString());
                    loginPromise = null;
                }
            }
        });
    }

    private WritableMap makeLoginResponse(VKAccessToken token) {
        WritableMap result = Arguments.createMap();

        result.putString(VKAccessToken.ACCESS_TOKEN, token.accessToken);
        result.putInt(VKAccessToken.EXPIRES_IN, token.expiresIn);
        result.putString(VKAccessToken.USER_ID, token.userId);
        result.putBoolean(VKAccessToken.HTTPS_REQUIRED, token.httpsRequired);
        result.putString(VKAccessToken.SECRET, token.secret);
        result.putString(VKAccessToken.EMAIL, token.email);

        return result;
    }
}
