package ru.volganap.nikolay.kids_monitor_ably;

import android.content.Intent;
import android.content.Context;
import android.util.Log;
import org.greenrobot.eventbus.EventBus;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpRequest implements KM_Constants{
    final String STORAGE_IS_EMPTY = "storage is empty";
    int attempt = 0;
    private static Context context;

    public void serverGetback(Context context, String sender, String command, String value) {
        String server_user;
        this.context = context;
        if (!sender.equals(PARENT_PHONE)) {
            server_user = KID_PHONE;
        } else {
            server_user = PARENT_PHONE;
        }
        OkHttpClient client = new OkHttpClient();
        Log.d(LOG_TAG, "OkHttpRequest. User is: " + sender + "; Location/command is: " + command);
        RequestBody formBody = new FormBody.Builder()
                .add("user", server_user)
                .add("command", command)
                .add("value", value)
                .build();
        Request request = new Request.Builder()
                .url(URL_ADDR)
                .post(formBody)
                .build();

        //исполняем запрос асинхронно
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, IOException e) {
                Log.d(LOG_TAG, "OkHttpRequest: Server ERROR is: " + e.toString());
                if (sender.equals(PARENT_PHONE)) {
                    EventBus.getDefault().postSticky(new EventBus_Parent(NET_ERROR_STATE));
                } else {
                    if (attempt > 3 ) {
                        callbackKidservice(context, sender, NET_ERROR_GOT_LOCATION_STATE + STA_SIGN + command);
                    } else {
                        Log.d(LOG_TAG, "OkHttpRequest: attempt: " + attempt + ", onFailure: " + e.toString());
                        attempt++;
                        serverGetback(context, sender, command, value);
                    }
                }
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                String res = response.body().string();
                Log.d(LOG_TAG, "OkHttpRequest: Get back with server, response is: " + res);
                if (sender.equals(PARENT_PHONE)) {
                    String message = "";
                    if (value.equals("")) {
                        if (res.equals(STORAGE_IS_EMPTY)) message = EMPTY_STORAGE_STATE;
                           else message = OK_STATE_PARENT + STA_SIGN + res;
                    } else message = CONFIG_SERVER_STATE + STA_SIGN + res;
                    Log.d(LOG_TAG, "OkHttpRequest: onResponse:  call EventBus_Parent " + res);
                    EventBus.getDefault().postSticky(new EventBus_Parent(message));
                } else {
                    Log.d(LOG_TAG, "OkHttpRequest: onResponse:  callbackKidservice " + command);
                    //callbackKidservice(context, sender, OK_STATE + STA_SIGN + command);
                    callbackKidservice(context, sender, OK_STATE_KID + STA_SIGN + command);
                }
            }
        });
    }

    public void callbackKidservice(Context context, String sender, String message) {
        Intent intent = new Intent();
        intent.setAction(ACTION_FROM_OKHTTP);
        intent.putExtra(SENDER, sender);
        intent.putExtra(MESSAGE, message);
        context.sendBroadcast(intent);
    }
}

